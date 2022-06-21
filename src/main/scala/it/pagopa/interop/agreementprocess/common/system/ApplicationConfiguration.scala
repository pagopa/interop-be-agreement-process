package it.pagopa.interop.agreementprocess.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  System.setProperty("kanela.show-banner", "false")
  val config: Config = ConfigFactory.load()

  val serverPort: Int = config.getInt("agreement-process.port")

  val agreementManagementURL: String         = config.getString("agreement-process.services.agreement-management")
  val catalogManagementURL: String           = config.getString("agreement-process.services.catalog-management")
  val partyManagementURL: String             = config.getString("agreement-process.services.party-management")
  val attributeRegistryManagementURL: String =
    config.getString("agreement-process.services.attribute-registry-management")
  val authorizationManagementURL: String     = config.getString("agreement-process.services.authorization-management")

  val jwtAudience: Set[String] = config.getString("agreement-process.jwt.audience").split(",").toSet.filter(_.nonEmpty)

  require(jwtAudience.nonEmpty, "Audience cannot be empty")

  val partyManagementApiKey: String = config.getString("agreement-process.api-keys.party-management")

  val storageContainer: String = config.getString("agreement-process.storage.container")
  val storagePath: String      = config.getString("agreement-process.storage.agreement-document-path")

  val agreementDocTemplatePath: String = config.getString("agreement-process.agreement-doc-template-path")
}
