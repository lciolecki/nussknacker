package pl.touk.nussknacker.engine.api.namespaces

import com.typesafe.config.Config

trait ObjectNaming extends Serializable {
  def prepareName(originalName: String, config: Config, namingContext: NamingContext): String
  def objectNamingParameters(originalName: String, config: Config, namingContext: NamingContext): Option[ObjectNamingParameters]
}

trait ObjectNamingParameters {
  /**
   * This function is used in [[pl.touk.nussknacker.engine.process.runner.FlinkProcessMain FlinkProcessMain]] to pass
   * to the [[pl.touk.nussknacker.engine.flink.api.NkGlobalParameters NkGlobalParameters]] tags that are to be used when
   * producing metrics in [[pl.touk.nussknacker.engine.flink.util.metrics.MetricUtils MetricUtils]]. It may be changed in the future.
   */
  def toTags: Map[String, String]
}

case object DefaultObjectNaming extends ObjectNaming {
  override def prepareName(originalName: String, config: Config, namingContext: NamingContext): String = originalName
  override def objectNamingParameters(originalName: String, config: Config, namingContext: NamingContext): Option[ObjectNamingParameters] = None
}