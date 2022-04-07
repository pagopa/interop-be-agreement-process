package it.pagopa.interop.agreementprocess.service.impl

import it.pagopa.interop.agreementprocess.service.{PartyManagementInvoker, PartyManagementService}
import it.pagopa.interop.partymanagement.client.api.PartyApi
import it.pagopa.interop.partymanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.interop.partymanagement.client.model.{Attribute, Institution}
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

  override def getInstitution(bearerToken: String)(partyId: UUID): Future[Institution] = {
    val request: ApiRequest[Institution] = partyApi.getInstitutionById(partyId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving Institution of party $partyId")
  }

}
