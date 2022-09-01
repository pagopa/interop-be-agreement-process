package it.pagopa.interop.agreementprocess.util

import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.attributeregistrymanagement
import it.pagopa.interop.attributeregistrymanagement.client.model.AttributeKind
import it.pagopa.interop.authorizationmanagement.client.model._
import it.pagopa.interop.catalogmanagement.client.model.{Attributes, EService, EServiceTechnology}
import it.pagopa.interop.selfcare.partymanagement.client.model.{Attribute, Institution}
import it.pagopa.interop.tenantmanagement.client.model.{ExternalId, Tenant}

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/**
 * Holds fake implementation of dependencies for tests not requiring neither mocks or stubs
 */
object FakeDependencies {

  class FakeAttributeManagementService extends AttributeManagementService {
    val attribute: ClientAttribute = attributeregistrymanagement.client.model.Attribute(
      id = "String",
      kind = AttributeKind.DECLARED,
      description = "fake",
      name = "fake",
      creationTime = OffsetDateTime.now()
    )

    override def getAttribute(attributeId: String)(implicit contexts: Seq[(String, String)]): Future[ClientAttribute] =
      Future.successful(attribute)

    override def getAttributeByOriginAndCode(origin: String, code: String)(implicit
      contexts: Seq[(String, String)]
    ): Future[ClientAttribute] = Future.successful(attribute)
  }

  class FakePartyManagementService extends PartyManagementService {
    override def getPartyAttributes(
      partyId: UUID
    )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Seq[Attribute]] =
      Future.successful(Seq.empty)

    override def getInstitution(
      partyId: UUID
    )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution] =
      Future.successful(
        Institution(
          id = UUID.randomUUID(),
          externalId = "fake",
          originId = "fake",
          description = "fake",
          digitalAddress = "fake",
          address = "fake",
          zipCode = "fake",
          taxCode = "fake",
          origin = "fake",
          institutionType = "fake",
          attributes = Seq.empty[Attribute]
        )
      )
  }

  class FakeAgreementManagementService extends AgreementManagementService {
    val agreement: Agreement = Agreement(
      id = UUID.randomUUID(),
      eserviceId = UUID.randomUUID(),
      descriptorId = UUID.randomUUID(),
      producerId = UUID.randomUUID(),
      consumerId = UUID.randomUUID(),
      state = AgreementState.ACTIVE,
      certifiedAttributes = Nil,
      declaredAttributes = Nil,
      verifiedAttributes = Nil,
      consumerDocuments = Nil,
      createdAt = OffsetDateTime.now()
    )

    override def createAgreement(producerId: UUID, consumerId: UUID, eServiceId: UUID, descriptorId: UUID)(implicit
      contexts: Seq[(String, String)]
    ): Future[Agreement] = Future.successful(agreement)

    override def getAgreementById(agreementId: String)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
      Future.successful(agreement)

    override def getAgreements(
      producerId: Option[String],
      consumerId: Option[String],
      eserviceId: Option[String],
      descriptorId: Option[String],
      states: List[AgreementState]
    )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]] = Future.successful(Seq.empty)

    override def upgradeById(agreementId: UUID, agreementSeed: UpgradeAgreementSeed)(implicit
      contexts: Seq[(String, String)]
    ): Future[Agreement] = Future.successful(agreement)

    override def updateAgreement(agreementId: UUID, seed: UpdateAgreementSeed)(implicit
      contexts: Seq[(String, String)]
    ): Future[Agreement] = Future.successful(agreement)
  }

  class FakeCatalogManagementService       extends CatalogManagementService       {
    override def getEServiceById(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService] =
      Future.successful(
        EService(
          id = UUID.randomUUID(),
          producerId = UUID.randomUUID(),
          name = "fake",
          description = "fake",
          technology = EServiceTechnology.REST,
          attributes = Attributes(Seq.empty, Seq.empty, Seq.empty),
          descriptors = Seq.empty
        )
      )
  }
  class FakeAuthorizationManagementService extends AuthorizationManagementService {
    override def updateStateOnClients(
      eServiceId: UUID,
      consumerId: UUID,
      agreementId: UUID,
      state: ClientComponentState
    )(implicit contexts: Seq[(String, String)]): Future[Unit] = Future.successful(())
  }

  class FakeTenantManagementService extends TenantManagementService {
    override def getTenant(tenantId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant] =
      Future.successful(
        Tenant(
          id = UUID.randomUUID(),
          selfcareId = Some(UUID.randomUUID().toString),
          externalId = ExternalId("origin", "value"),
          features = Nil,
          attributes = Nil,
          createdAt = OffsetDateTime.now(),
          updatedAt = None
        )
      )
  }
}
