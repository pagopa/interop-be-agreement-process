package it.pagopa.pdnd.interop.uservice.agreementprocess.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.pdnd.interop.uservice._
import spray.json.DefaultJsonProtocol

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  type ManagementEService     = catalogmanagement.client.model.EService
  type ManagementOrganization = partymanagement.client.model.Organization
  type ManagementAgreement    = agreementmanagement.client.model.Agreement
  type ManagementAttributes   = catalogmanagement.client.model.Attributes

}
