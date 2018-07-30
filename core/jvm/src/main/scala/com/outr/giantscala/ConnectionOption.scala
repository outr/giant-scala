package com.outr.giantscala

sealed trait ConnectionOption {
  def key: String
  def value: String

  override def toString: String = s"$key=$value"
}

object ConnectionOption {
  case class ReplicaSet(value: String) extends ConnectionOption {
    override def key: String = "replicaSet"
  }
  case class SSL(enable: Boolean = true) extends ConnectionOption {
    override def key: String = "ssl"
    override def value: String = enable.toString
  }
  case class ConnectionTimeout(millis: Long) extends ConnectionOption {
    override def key: String = "connectionTimeoutMS"
    override def value: String = millis.toString
  }
  case class SocketTimeout(millis: Long) extends ConnectionOption {
    override def key: String = "socketTimeoutMS"
    override def value: String = millis.toString
  }
  case class MaxPoolSize(connections: Int = 100) extends ConnectionOption {
    override def key: String = "maxPoolSize"
    override def value: String = connections.toString
  }
  case class MinPoolSize(connections: Int = 0) extends ConnectionOption {
    override def key: String = "minPoolSize"
    override def value: String = connections.toString
  }
  case class MaxIdleTime(millis: Long) extends ConnectionOption {
    override def key: String = "maxIdleTimeMS"
    override def value: String = millis.toString
  }
  case class WaitQueueMultiple(multiple: Int) extends ConnectionOption {
    override def key: String = "waitQueueMultiple"
    override def value: String = multiple.toString
  }
  case class WaitQueueTimeout(millis: Long) extends ConnectionOption {
    override def key: String = "waitQueueTimeoutMS"
    override def value: String = millis.toString
  }
  sealed trait ReadPreference extends ConnectionOption {
    override def key: String = "readPreference"
  }
  object ReadPreference {
    case object Primary extends ReadPreference {
      override def value: String = "primary"
    }
    case object PrimaryPreferred extends ReadPreference {
      override def value: String = "primaryPreferred"
    }
    case object Secondary extends ReadPreference {
      override def value: String = "secondary"
    }
    case object SecondaryPreferred extends ReadPreference {
      override def value: String = "secondaryPreferred"
    }
    case object Nearest extends ReadPreference {
      override def value: String = "nearest"
    }
  }
  case class MaxStaleness(seconds: Int) extends ConnectionOption {
    override def key: String = "maxStalenessSeconds"
    override def value: String = seconds.toString
  }
  case class ReadPreferenceTags(tags: Map[String, String]) extends ConnectionOption {
    override def key: String = "readPreferenceTags"
    override def value: String = tags.map(t => s"${t._1}:${t._2}").mkString(",")
  }
  case class AuthSource(value: String) extends ConnectionOption {
    override def key: String = "authSource"
  }
  case class AuthMechanism(value: String) extends ConnectionOption {
    override def key: String = "authMechanism"
  }
  case class GSSAPIServiceName(value: String) extends ConnectionOption {
    override def key: String = "gssapiServiceName"
  }
  case class LocalThreshold(millis: Long) extends ConnectionOption {
    override def key: String = "localThresholdMS"
    override def value: String = millis.toString
  }
  case class ServerSelectionTimeout(millis: Long) extends ConnectionOption {
    override def key: String = "serverSelectionTimeoutMS"
    override def value: String = millis.toString
  }
  case class ServerSelectionTryOnce(onlyOnce: Boolean = true) extends ConnectionOption {
    override def key: String = "serverSelectionTryOnce"
    override def value: String = onlyOnce.toString
  }
  case class HeartbeatFrequency(millis: Long) extends ConnectionOption {
    override def key: String = "heartbeatFrequencyMS"
    override def value: String = millis.toString
  }
  case class AppName(value: String) extends ConnectionOption {
    override def key: String = "appName"
  }
  case class RetryWrites(retry: Boolean = true) extends ConnectionOption {
    override def key: String = "retryWrites"
    override def value: String = retry.toString
  }
  case class UUIDRepresentation(value: String) extends ConnectionOption {
    override def key: String = "uuidRepresentation"
  }
}