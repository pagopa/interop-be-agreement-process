package it.pagopa.pdnd.interop.uservice.agreementprocess.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  def serverPort: Int = {
    config.getInt("uservice-agreement-process.port")
  }

  def agreementManagementURL: String = config.getString("services.agreement-management")

  def catalogManagementURL: String = config.getString("services.catalog-management")

  def partyManagementURL: String = config.getString("services.party-management")

  def attributeRegistryManagementURL: String = config.getString("services.attribute-registry-management")

}
