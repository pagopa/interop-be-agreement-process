package it.pagopa.pdnd.interop.uservice.agreementprocess.service.impl

import it.pagopa.pdnd.interop.uservice.agreementprocess.service.{PartyManagementInvoker, PartyManagementService}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.api.PartyApi

import scala.concurrent.Future

final case class PartyManagementServiceImpl(partyManagementInvoker: PartyManagementInvoker, partyApi: PartyApi)
    extends PartyManagementService {
  override def getConsumerAttributes(bearerToken: String, agreementId: String): Future[Seq[String]] = ???
}
