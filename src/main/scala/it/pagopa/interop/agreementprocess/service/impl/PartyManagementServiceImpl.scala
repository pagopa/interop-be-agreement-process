package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.agreementprocess.service.{PartyManagementInvoker, PartyManagementService}
import it.pagopa.interop.partymanagement.client.api.PartyApi
import it.pagopa.interop.partymanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.interop.partymanagement.client.model.{Attribute, Organization}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

final case class PartyManagementServiceImpl(invoker: PartyManagementInvoker, partyApi: PartyApi)
    extends PartyManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getPartyAttributes(bearerToken: String)(partyId: UUID): Future[Seq[Attribute]] = {
    val request: ApiRequest[Seq[Attribute]] = partyApi.getPartyAttributes(partyId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving Attributes of party $partyId")
  }

  override def getOrganization(bearerToken: String)(partyId: UUID): Future[Organization] = {
    val request: ApiRequest[Organization] = partyApi.getOrganizationById(partyId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving Organization of party $partyId")
  }

}
