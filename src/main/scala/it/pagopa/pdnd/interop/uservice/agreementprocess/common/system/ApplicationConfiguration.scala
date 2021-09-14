package it.pagopa.pdnd.interop.uservice.agreementprocess.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  def serverPort: Int = {
    config.getInt("uservice-agreement-process.port")
  }

  def agreementManagementURL: String = {
//    val agreementMgmtURL: String = config.getString("services.agreement-management")
//    s"$agreementMgmtURL/pdnd-interop-uservice-agreement-management/0.0.1"
    s"https://gateway.interop.pdnd.dev/pdnd-interop-uservice-agreement-management/0.0.1"
  }

  def catalogManagementURL: String = {
//    val catalogMgmtURL: String = config.getString("services.catalog-management")
//    s"$catalogMgmtURL/pdnd-interop-uservice-catalog-management/0.0.1"
    s"https://gateway.interop.pdnd.dev/pdnd-interop-uservice-catalog-management/0.0.1"
  }

  def partyManagementURL: String = {
//    val partyMgmtURL: String = config.getString("services.party-management")
//    s"$partyMgmtURL/pdnd-interop-uservice-party-management/0.0.1"
    s"https://gateway.interop.pdnd.dev/pdnd-interop-uservice-catalog-management/0.0.1"
  }

  def attributeRegistryManagementURL: String = {
//    val attributeRegistryMgmtURL: String = config.getString("services.attribute-registry-management")
//    s"$attributeRegistryMgmtURL/pdnd-interop-uservice-attribute-registry-management/0.0.1"
    s"https://gateway.interop.pdnd.dev/pdnd-interop-uservice-attribute-registry-management/0.0.1"
  }

}
