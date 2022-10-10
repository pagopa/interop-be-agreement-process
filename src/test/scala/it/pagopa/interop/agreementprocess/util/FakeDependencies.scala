package it.pagopa.interop.agreementprocess.util

import it.pagopa.interop.agreementmanagement.client.model._
import it.pagopa.interop.agreementprocess.service._
import it.pagopa.interop.attributeregistrymanagement
import it.pagopa.interop.attributeregistrymanagement.client.model.AttributeKind
import it.pagopa.interop.authorizationmanagement.client.model._
import it.pagopa.interop.catalogmanagement.client.model.{Attributes, EService, EServiceTechnology}
import it.pagopa.interop.tenantmanagement.client.model.{ExternalId, Tenant}

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Future

/**
 * Holds fake implementation of dependencies for tests not requiring neither mocks or stubs
 */
object FakeDependencies {

  class FakeAttributeManagementService extends AttributeManagementService {
    val attribute: ClientAttribute = attributeregistrymanagement.client.model.Attribute(
      id = UUID.randomUUID(),
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
      states: List[AgreementState],
      attributeId: Option[String] = None
    )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]] = Future.successful(Seq.empty)

    override def upgradeById(agreementId: UUID, agreementSeed: UpgradeAgreementSeed)(implicit
      contexts: Seq[(String, String)]
    ): Future[Agreement] = Future.successful(agreement)

    override def updateAgreement(agreementId: UUID, seed: UpdateAgreementSeed)(implicit
      contexts: Seq[(String, String)]
    ): Future[Agreement] = Future.successful(agreement)

    override def deleteAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit] =
      Future.unit
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
    )(implicit contexts: Seq[(String, String)]): Future[Unit] = Future.unit

    override def updateAgreementAndEServiceStates(
      eServiceId: UUID,
      consumerId: UUID,
      payload: ClientAgreementAndEServiceDetailsUpdate
    )(implicit contexts: Seq[(String, String)]): Future[Unit] = Future.unit
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
