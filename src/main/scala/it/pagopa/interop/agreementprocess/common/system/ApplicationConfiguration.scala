package it.pagopa.interop.agreementprocess.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  lazy val serverPort: Int = config.getInt("agreement-process.port")

  lazy val agreementManagementURL: String         = config.getString("agreement-process.services.agreement-management")
  lazy val catalogManagementURL: String           = config.getString("agreement-process.services.catalog-management")
  lazy val partyManagementURL: String             = config.getString("agreement-process.services.party-management")
  lazy val attributeRegistryManagementURL: String =
    config.getString("agreement-process.services.attribute-registry-management")
  lazy val authorizationManagementURL: String = config.getString("agreement-process.services.authorization-management")

  lazy val jwtAudience: Set[String] = config.getString("agreement-process.jwt.audience").split(",").toSet
}
