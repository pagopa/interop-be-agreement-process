package it.pagopa.interop.agreementprocess.error

import akka.http.scaladsl.server.StandardRoute
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors._
import it.pagopa.interop.commons.logging.ContextFieldsToLog
import it.pagopa.interop.commons.utils.errors.{AkkaResponses, ServiceCode}

import scala.util.{Failure, Try}

object Handlers extends AkkaResponses {

  implicit val serviceCode: ServiceCode = ServiceCode("005")

  def handleCreationError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: DescriptorNotInExpectedState) => badRequest(ex, logMessage)
    case Failure(ex: AgreementAlreadyExists)       => badRequest(ex, logMessage)
    case Failure(ex: MissingCertifiedAttributes)   => badRequest(ex, logMessage)
    case Failure(ex: EServiceNotFound)             => badRequest(ex, logMessage)
    case Failure(ex)                               => internalServerError(ex, logMessage)
  }

  def handleSubmissionError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: AgreementNotFound)            => notFound(ex, logMessage)
    case Failure(ex: AgreementAlreadyExists)       => badRequest(ex, logMessage)
    case Failure(ex: AgreementNotInExpectedState)  => badRequest(ex, logMessage)
    case Failure(ex: AgreementSubmissionFailed)    => badRequest(ex, logMessage)
    case Failure(ex: MissingCertifiedAttributes)   => badRequest(ex, logMessage)
    case Failure(ex: DescriptorNotInExpectedState) => badRequest(ex, logMessage)
    case Failure(ex: OperationNotAllowed)          => forbidden(ex, logMessage)
    case Failure(ex)                               => internalServerError(ex, logMessage)
  }

  def handleActivationError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: AgreementNotFound)            => notFound(ex, logMessage)
    case Failure(ex: AgreementAlreadyExists)       => badRequest(ex, logMessage)
    case Failure(ex: AgreementActivationFailed)    => badRequest(ex, logMessage)
    case Failure(ex: AgreementNotInExpectedState)  => badRequest(ex, logMessage)
    case Failure(ex: OperationNotAllowed)          => forbidden(ex, logMessage)
    case Failure(ex: DescriptorNotInExpectedState) => badRequest(ex, logMessage)
    case Failure(ex)                               => internalServerError(ex, logMessage)
  }

  def handleRejectionError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: AgreementNotFound)           => notFound(ex, logMessage)
    case Failure(ex: AgreementNotInExpectedState) => badRequest(ex, logMessage)
    case Failure(ex: OperationNotAllowed)         => forbidden(ex, logMessage)
    case Failure(ex)                              => internalServerError(ex, logMessage)
  }

  def handleSuspensionError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: AgreementNotFound)           => notFound(ex, logMessage)
    case Failure(ex: AgreementNotInExpectedState) => badRequest(ex, logMessage)
    case Failure(ex: OperationNotAllowed)         => forbidden(ex, logMessage)
    case Failure(ex)                              => internalServerError(ex, logMessage)
  }

  def handleDeletionError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: AgreementNotFound)           => notFound(ex, logMessage)
    case Failure(ex: AgreementNotInExpectedState) => badRequest(ex, logMessage)
    case Failure(ex: OperationNotAllowed)         => forbidden(ex, logMessage)
    case Failure(ex)                              => internalServerError(ex, logMessage)
  }

  def handleUpgradeError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: AgreementNotFound)           => notFound(ex, logMessage)
    case Failure(ex: OperationNotAllowed)         => forbidden(ex, logMessage)
    case Failure(ex: AgreementNotInExpectedState) => badRequest(ex, logMessage)
    case Failure(ex: PublishedDescriptorNotFound) => badRequest(ex, logMessage)
    case Failure(ex: NoNewerDescriptorExists)     => badRequest(ex, logMessage)
    case Failure(ex)                              => internalServerError(ex, logMessage)
  }

  def handleRetrieveError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: AgreementNotFound) => notFound(ex, logMessage)
    case Failure(ex)                    => internalServerError(ex, logMessage)
  }

  def handleListingError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = { case Failure(ex) =>
    internalServerError(ex, logMessage)
  }

  def handleComputeAgreementsStateError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = { case Failure(ex) =>
    internalServerError(ex, logMessage)
  }

  def handleAddDocumentError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: AgreementNotFound) => notFound(ex, logMessage)
    case Failure(ex)                    => internalServerError(ex, logMessage)
  }

  def handleGetDocumentError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: DocumentNotFound) => notFound(ex, logMessage)
    case Failure(ex)                   => internalServerError(ex, logMessage)
  }

}
