package io.github.novakovalexey.krboperator

import java.nio.file.Path

import cats.Parallel
import cats.effect.{ConcurrentEffect, Sync}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import freya.OperatorCfg.Crd
import freya.{Controller, Metadata}
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.openshift.client.OpenShiftClient
import io.github.novakovalexey.krboperator.KrbController._
import io.github.novakovalexey.krboperator.service._

object KrbController {
  val checkMark: String = "\u2714"
}

class KrbController[F[_]: Parallel: ConcurrentEffect](
  client: OpenShiftClient,
  cfg: Crd[Krb],
  operatorCfg: KrbOperatorCfg,
  template: Template[F, _ <: HasMetadata],
  kadmin: Kadmin[F],
  secret: SecretService[F]
)(implicit F: Sync[F])
    extends Controller[F, Krb]
    with LazyLogging {

  override def onAdd(krb: Krb, meta: Metadata): F[Unit] = {
    logger.info(s"add event: $krb, $meta")

    for {
      _ <- template.findService(meta) match {
        case Some(_) =>
          logger.info(s"$checkMark [${meta.name}] Service is found, so skipping its creation")
          F.unit
        case None =>
          for {
            _ <- template.createService(meta)
            _ = logger.info(s"$checkMark Service ${meta.name} created")
          } yield ()
      }
      _ <- secret.findAdminSecret(meta) match {
        case Some(_) =>
          logger.info(s"$checkMark [${meta.name}] Admin Secret is found, so skipping its creation")
          F.unit
        case None =>
          for {
            _ <- secret.createAdminSecret(meta, template.adminSecretSpec)
            _ = logger.info(s"$checkMark Admin secret ${meta.name} created")
          } yield ()
      }
      _ <- template.findDeployment(meta) match {
        case Some(_) =>
          logger.info(s"$checkMark [${meta.name}] Deployment is found, so skipping its creation")
          F.unit
        case None =>
          for {
            _ <- template.createDeployment(meta, krb.realm)
            _ <- template.waitForDeployment(meta)
            _ = logger.info(s"$checkMark deployment ${meta.name} created")
          } yield ()
      }

      missingSecrets <- secret.findMissing(meta, krb.principals.map(_.secret).toSet)
      _ <- createSecrets(krb, meta, missingSecrets)
    } yield ()
  }

  private def createSecrets(krb: Krb, meta: Metadata, missingSecrets: Set[String]) = {
    logger.info(s"There are ${missingSecrets.size} missing secrets")

    lazy val adminPwd = secret.getAdminPwd(meta)
    val r = missingSecrets.map(s => (s, krb.principals.filter(_.secret == s))).map {
      case (secretName, ps) =>
        for {
          pwd <- adminPwd
          state <- kadmin.createPrincipalsAndKeytabs(ps, KadminContext(krb.realm, meta, pwd))
          statuses <- copyKeytabs(meta.namespace, state)
          _ <- if (statuses.forall { case (_, copied) => copied })
            F.unit
          else
            F.raiseError[Unit](new RuntimeException(s"Failed to upload keytabs ${statuses.filter {
              case (_, copied) => !copied
            }.map { case (path, _) => path }} into POD"))
          _ <- secret.createSecret(meta.namespace, state.keytabs, secretName)
          _ = logger.info(s"$checkMark Keytab secret $secretName created")
        } yield ()
    }
    r.toList.parSequence
  }

  private def copyKeytabs(namespace: String, state: KerberosState): F[List[(Path, Boolean)]] =
    F.delay(state.keytabs.foldLeft(List.empty[(Path, Boolean)]) {
      case (acc, keytab) =>
        logger.debug(s"Copying keytab '$keytab' from $namespace/${state.podName} POD")
        acc :+ (keytab.path, client.pods
          .inNamespace(namespace)
          .withName(state.podName)
          .inContainer(operatorCfg.kadminContainer)
          .file(keytab.path.toString)
          .copy(keytab.path))
        //TODO: remove keytab folder in the Kadmin container
    })

  override def onDelete(krb: Krb, meta: Metadata): F[Unit] = {
    logger.info(s"delete event: $krb, $meta")
    for {
      _ <- template.delete(krb, meta)
      _ <- secret.deleteSecrets(meta.namespace)
    } yield ()
  }
}
