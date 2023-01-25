package it.pagopa.interop.agreementprocess.common

import cats.implicits._
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.AgreementNotInExpectedState
import it.pagopa.interop.agreementprocess.model._
import it.pagopa.interop.agreementmanagement.model.agreement._

import java.util.UUID

object Adapters {

  val ACTIVABLE_STATES: Set[AgreementManagement.AgreementState]   =
    Set(AgreementManagement.AgreementState.PENDING, AgreementManagement.AgreementState.SUSPENDED)
  val SUSPENDABLE_STATES: Set[AgreementManagement.AgreementState] =
    Set(AgreementManagement.AgreementState.ACTIVE, AgreementManagement.AgreementState.SUSPENDED)
  val ARCHIVABLE_STATES: Set[AgreementManagement.AgreementState]  =
    Set(AgreementManagement.AgreementState.ACTIVE, AgreementManagement.AgreementState.SUSPENDED)
  val SUBMITTABLE_STATES: Set[AgreementManagement.AgreementState] = Set(AgreementManagement.AgreementState.DRAFT)
  val UPDATEABLE_STATES: Set[AgreementManagement.AgreementState]  =
    Set(AgreementManagement.AgreementState.DRAFT)
  val UPGRADABLE_STATES: Set[AgreementManagement.AgreementState]  =
    Set(AgreementManagement.AgreementState.ACTIVE, AgreementManagement.AgreementState.SUSPENDED)
  val REJECTABLE_STATES: Set[AgreementManagement.AgreementState]  = Set(AgreementManagement.AgreementState.PENDING)
  val DELETABLE_STATES: Set[AgreementManagement.AgreementState]   =
    Set(AgreementManagement.AgreementState.DRAFT, AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES)

  implicit class AgreementWrapper(private val a: AgreementManagement.Agreement) extends AnyVal {

    def assertSubmittableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(a.id.toString, a.state))
      .withRight[Unit]
      .unlessA(SUBMITTABLE_STATES.contains(a.state))

    def assertActivableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(a.id.toString, a.state))
      .withRight[Unit]
      .unlessA(ACTIVABLE_STATES.contains(a.state))

    def assertSuspendableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(a.id.toString, a.state))
      .withRight[Unit]
      .unlessA(SUSPENDABLE_STATES.contains(a.state))

    def assertArchivableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(a.id.toString, a.state))
      .withRight[Unit]
      .unlessA(ARCHIVABLE_STATES.contains(a.state))

    def assertUpdateableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(a.id.toString, a.state))
      .withRight[Unit]
      .unlessA(UPDATEABLE_STATES.contains(a.state))

    def assertUpgradableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(a.id.toString, a.state))
      .withRight[Unit]
      .unlessA(UPGRADABLE_STATES.contains(a.state))

    def assertRejectableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(a.id.toString, a.state))
      .withRight[Unit]
      .unlessA(REJECTABLE_STATES.contains(a.state))

    def assertDeletableState: Either[Throwable, Unit] = Left(AgreementNotInExpectedState(a.id.toString, a.state))
      .withRight[Unit]
      .unlessA(DELETABLE_STATES.contains(a.state))

    def toApi: Agreement = Agreement(
      id = a.id,
      eserviceId = a.eserviceId,
      descriptorId = a.descriptorId,
      producerId = a.producerId,
      consumerId = a.consumerId,
      state = a.state.toApi,
      verifiedAttributes = a.verifiedAttributes.map(_.toApi),
      certifiedAttributes = a.certifiedAttributes.map(_.toApi),
      declaredAttributes = a.declaredAttributes.map(_.toApi),
      suspendedByConsumer = a.suspendedByConsumer,
      suspendedByProducer = a.suspendedByProducer,
      suspendedByPlatform = a.suspendedByPlatform,
      consumerDocuments = a.consumerDocuments.map(_.toApi),
      consumerNotes = a.consumerNotes,
      rejectionReason = a.rejectionReason,
      createdAt = a.createdAt,
      updatedAt = a.updatedAt,
      contract = a.contract.map(_.toApi)
    )
  }

  implicit class AgreementStateWrapper(private val s: AgreementManagement.AgreementState) extends AnyVal {
    def toApi: AgreementState = s match {
      case AgreementManagement.AgreementState.DRAFT                        => AgreementState.DRAFT
      case AgreementManagement.AgreementState.PENDING                      => AgreementState.PENDING
      case AgreementManagement.AgreementState.ACTIVE                       => AgreementState.ACTIVE
      case AgreementManagement.AgreementState.SUSPENDED                    => AgreementState.SUSPENDED
      case AgreementManagement.AgreementState.ARCHIVED                     => AgreementState.ARCHIVED
      case AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES =>
        AgreementState.MISSING_CERTIFIED_ATTRIBUTES
      case AgreementManagement.AgreementState.REJECTED                     => AgreementState.REJECTED
    }
  }

  implicit class VerifiedAttributeWrapper(private val a: AgreementManagement.VerifiedAttribute) extends AnyVal {
    def toApi: VerifiedAttribute = VerifiedAttribute(id = a.id)
  }

  implicit class CertifiedAttributeWrapper(private val a: AgreementManagement.CertifiedAttribute) extends AnyVal {
    def toApi: CertifiedAttribute = CertifiedAttribute(id = a.id)
  }

  implicit class DeclaredAttributeWrapper(private val a: AgreementManagement.DeclaredAttribute) extends AnyVal {
    def toApi: DeclaredAttribute = DeclaredAttribute(id = a.id)
  }

  implicit class DocumentSeedWrapper(private val d: AgreementManagement.DocumentSeed) extends AnyVal {
    def toApi: DocumentSeed =
      DocumentSeed(id = d.id, name = d.name, prettyName = d.prettyName, contentType = d.contentType, path = d.path)
  }

  implicit class DocumentWrapper(private val d: AgreementManagement.Document) extends AnyVal {
    def toApi: Document = Document(
      id = d.id,
      name = d.name,
      prettyName = d.prettyName,
      contentType = d.contentType,
      createdAt = d.createdAt,
      path = d.path
    )
  }

  implicit class AgreementPayloadWrapper(private val p: AgreementPayload) extends AnyVal {
    def toSeed(producerId: UUID, consumerId: UUID): AgreementManagement.AgreementSeed =
      AgreementManagement.AgreementSeed(
        eserviceId = p.eserviceId,
        descriptorId = p.descriptorId,
        producerId = producerId,
        consumerId = consumerId,
        verifiedAttributes = Nil,
        certifiedAttributes = Nil,
        declaredAttributes = Nil
      )
  }

  implicit class PersistentAgreementWrapper(private val p: PersistentAgreement) extends AnyVal {
    def toApi: Agreement = Agreement(
      id = p.id,
      eserviceId = p.eserviceId,
      descriptorId = p.descriptorId,
      producerId = p.producerId,
      consumerId = p.consumerId,
      state = p.state.toApi,
      verifiedAttributes = p.verifiedAttributes.map(_.toApi),
      certifiedAttributes = p.certifiedAttributes.map(_.toApi),
      declaredAttributes = p.declaredAttributes.map(_.toApi),
      suspendedByConsumer = p.suspendedByConsumer,
      suspendedByProducer = p.suspendedByProducer,
      suspendedByPlatform = p.suspendedByPlatform,
      consumerNotes = p.consumerNotes,
      rejectionReason = p.rejectionReason,
      consumerDocuments = p.consumerDocuments.map(_.toApi),
      createdAt = p.createdAt,
      updatedAt = p.updatedAt,
      contract = p.contract.map(_.toApi)
    )
  }

  implicit class PersistentAgreementStateWrapper(private val p: PersistentAgreementState)         extends AnyVal {
    def toApi: AgreementState = p match {
      case Draft                      => AgreementState.DRAFT
      case Active                     => AgreementState.ACTIVE
      case Archived                   => AgreementState.ARCHIVED
      case MissingCertifiedAttributes => AgreementState.MISSING_CERTIFIED_ATTRIBUTES
      case Pending                    => AgreementState.PENDING
      case Rejected                   => AgreementState.REJECTED
      case Suspended                  => AgreementState.SUSPENDED
    }
  }
  implicit class PersistentVerifiedAttributeWrapper(private val p: PersistentVerifiedAttribute)   extends AnyVal {
    def toApi: VerifiedAttribute = VerifiedAttribute(id = p.id)
  }
  implicit class PersistentCertifiedAttributeWrapper(private val p: PersistentCertifiedAttribute) extends AnyVal {
    def toApi: CertifiedAttribute = CertifiedAttribute(id = p.id)
  }
  implicit class PersistentDeclaredAttributeWrapper(private val p: PersistentDeclaredAttribute)   extends AnyVal {
    def toApi: DeclaredAttribute = DeclaredAttribute(id = p.id)
  }
  implicit class PersistentAgreementDocumentWrapper(private val p: PersistentAgreementDocument)   extends AnyVal {
    def toApi: Document = Document(
      id = p.id,
      name = p.name,
      prettyName = p.prettyName,
      contentType = p.contentType,
      createdAt = p.createdAt,
      path = p.path
    )
  }
}
