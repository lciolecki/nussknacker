package pl.touk.nussknacker.engine.flink.queryablestate

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.JobID
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.queryablestate.client.QueryableStateClient
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.QueryableState

import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object EspQueryableClient {

  def apply(queryableStateProxyUrl: String): EspQueryableClient = {

    //TODO: this is workaround for https://issues.apache.org/jira/browse/FLINK-10225, we want to be able to configure all task managers
    val queryableStateProxyUrls = queryableStateProxyUrl.split(",").toList
    val clients = queryableStateProxyUrls.map { url =>
      val (queryableStateProxyHost, queryableStateProxyPort) = parseHostAndPort(queryableStateProxyUrl)
      new QueryableStateClient(queryableStateProxyHost, queryableStateProxyPort)
    }
    new EspQueryableClient(clients)
  }

  private def parseHostAndPort(url: String): (String, Int) = {
    val parts = url.split(':')
    if (parts.length != 2)
      throw new IllegalArgumentException("Should by in host:port format")
    (parts(0), parts(1).toInt)
  }

}

class EspQueryableClient(clients: List[QueryableStateClient]) extends LazyLogging {

  def fetchState[V: TypeInformation](jobId: String, queryName: String, key: String)
                                    (implicit ec: ExecutionContext): Future[V] = {
    val keyTypeInfo = implicitly[TypeInformation[String]]
    val valueTypeInfo = implicitly[TypeInformation[V]]
    val flinkJobId = JobID.fromHexString(jobId)
    val stateDescriptor = new ValueStateDescriptor[V](key, valueTypeInfo)

    //TODO: this is workaround for https://issues.apache.org/jira/browse/FLINK-10225, we want to be able to configure all task managers
    def tryToFetchState(clientsToCheck: List[QueryableStateClient], lastException: Option[Throwable]): Future[V] = clientsToCheck match {
      case next::rest =>
        FutureConverters.toScala(next.getKvState(flinkJobId, queryName, key, keyTypeInfo, stateDescriptor)).map { valueState =>
          valueState.value()
        }.recoverWith {
          case NonFatal(e) =>
            logger.debug(s"Failed to fetch state from $next with ${e.getMessage}", e)
            tryToFetchState(rest, Some(e))
        }
      case Nil =>
        Future.failed(new IllegalArgumentException(
          s"Failed to retrieve queryable state, last exception: ${lastException.map(_.getMessage).getOrElse("")}", lastException.orNull))
    }

    tryToFetchState(clients, None)
  }

  def fetchJsonState(jobId: String, queryName: String, key: String)
                    (implicit ec: ExecutionContext): Future[String] = {
    fetchState[String](jobId, queryName, key)
  }

  def fetchJsonState(jobId: String, queryName: String)
                    (implicit ec: ExecutionContext): Future[String] = {
    fetchState[String](jobId, queryName)
  }

  def fetchState[V: TypeInformation](jobId: String, queryName: String)
                (implicit ec: ExecutionContext): Future[V] = {
    fetchState[V](jobId, queryName, QueryableState.defaultKey)
  }

}