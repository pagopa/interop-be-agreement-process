package it.pagopa.interop.agreementprocess.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  val config: Config = ConfigFactory.load()

  val serverPort: Int                    = config.getInt("agreement-process.port")
  val agreementManagementURL: String     = config.getString("agreement-process.services.agreement-management")
  val catalogManagementURL: String       = config.getString("agreement-process.services.catalog-management")
  val tenantManagementURL: String        = config.getString("agreement-process.services.tenant-management")
  val authorizationManagementURL: String = config.getString("agreement-process.services.authorization-management")
  val userRegistryURL: String            = config.getString("agreement-process.services.user-registry")
  val jwtAudience: Set[String] = config.getString("agreement-process.jwt.audience").split(",").toSet.filter(_.nonEmpty)
  val attributeRegistryManagementURL: String =
    config.getString("agreement-process.services.attribute-registry-management")

  require(jwtAudience.nonEmpty, "Audience cannot be empty")

  val storageKind: String           = config.getString("agreement-process.storage.kind")
  val storageContainer: String      = config.getString("agreement-process.storage.container")
  val agreementContractPath: String = config.getString("agreement-process.storage.agreement-contract-path")
  val userRegistryApiKey: String    = config.getString("agreement-process.user-registry-api-key")
}
