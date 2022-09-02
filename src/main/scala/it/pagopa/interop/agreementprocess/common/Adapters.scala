package it.pagopa.interop.agreementprocess.common

import cats.implicits._
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagement}
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.AgreementNotInExpectedState
import it.pagopa.interop.agreementprocess.model._

object Adapters {

  val ACTIVABLE_STATES: Set[AgreementManagement.AgreementState]   =
    Set(AgreementManagement.AgreementState.PENDING, AgreementManagement.AgreementState.SUSPENDED)
  val SUSPENDABLE_STATES: Set[AgreementManagement.AgreementState] =
    Set(AgreementManagement.AgreementState.ACTIVE, AgreementManagement.AgreementState.SUSPENDED)
  val ARCHIVABLE_STATES: Set[AgreementManagement.AgreementState]  =
    Set(AgreementManagement.AgreementState.ACTIVE, AgreementManagement.AgreementState.SUSPENDED)
  val SUBMITTABLE_STATES: Set[AgreementManagement.AgreementState] = Set(AgreementManagement.AgreementState.DRAFT)

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
      createdAt = a.createdAt,
      updatedAt = a.updatedAt
    )
  }

  implicit class AgreementStateWrapper(private val s: AgreementManagement.AgreementState) extends AnyVal {
    def toApi: AgreementState = s match {
      case AgreementManagement.AgreementState.DRAFT                        => AgreementState.DRAFT
      case AgreementManagement.AgreementState.PENDING                      => AgreementState.PENDING
      case AgreementManagement.AgreementState.ACTIVE                       => AgreementState.ACTIVE
      case AgreementManagement.AgreementState.SUSPENDED                    => AgreementState.SUSPENDED
      case AgreementManagement.AgreementState.INACTIVE                     => AgreementState.INACTIVE
      case AgreementManagement.AgreementState.MISSING_CERTIFIED_ATTRIBUTES =>
        AgreementState.MISSING_CERTIFIED_ATTRIBUTES
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

}
