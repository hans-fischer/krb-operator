import sbt._

object Dependencies extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  object autoImport {

    object DependenciesVersion {
      val catsVersion                      = "2.1.0"
      val logbackClassicVersion            = "1.3.0-alpha4"
      val pureConfigVersion                = "0.12.2"
      val scalaLoggingVersion              = "3.9.2"
      val fabric8K8sVersion                = "4.7.1"
      val codecsVersion                    = "1.14"
      val jacksonJsonSchemaV               = "1.0.36"
      val betterMonadicVersion             = "0.3.1"
      val freyaVersion                     = "0.1.4"
      val scalaTestVersion                 = "3.1.0"
      val scalaTestCheckVersion            = "3.1.0.0-RC2"
      val scalaCheckVersion                = "1.14.3"
    }

    import DependenciesVersion._

    val cats                     = "org.typelevel"             %%  "cats-core"                 % catsVersion
    val logbackClassic           = "ch.qos.logback"            %   "logback-classic"           % logbackClassicVersion
    val scalaLogging             = "com.typesafe.scala-logging" %% "scala-logging"             % scalaLoggingVersion
    val scalaTest                = "org.scalatest"             %%  "scalatest"                 % scalaTestVersion
    val scalaCheck               = "org.scalacheck"            %% "scalacheck"                 % scalaCheckVersion
    val scalaTestCheck           = "org.scalatestplus"         %% "scalatestplus-scalacheck"   % scalaTestCheckVersion
    val osClient                 = "io.fabric8"                % "openshift-client"            % fabric8K8sVersion
    val osServerMock             = "io.fabric8"                % "openshift-server-mock"       % fabric8K8sVersion
    val pureConfig               = "com.github.pureconfig"     %%  "pureconfig"                % pureConfigVersion
    val codecs                   = "commons-codec"             % "commons-codec"               % codecsVersion
    val betterMonadicFor         = "com.olegpy"                %% "better-monadic-for"         % betterMonadicVersion
    val freya                    = "io.github.novakov-alexey"  %% "freya"                      % freyaVersion
    val jacksonJsonSchema        = "com.kjetland"               %% "mbknor-jackson-jsonschema" % jacksonJsonSchemaV
  }
}
