package it.pagopa.interop.agreementprocess.api.impl

import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors._
import it.pagopa.interop.commons.logging.ContextFieldsToLog
import it.pagopa.interop.commons.utils.errors.{AkkaResponses, ServiceCode}

import scala.util.{Failure, Success, Try}

object ResponseHandlers extends AkkaResponses {

  implicit val serviceCode: ServiceCode = ServiceCode("005")

  def createAgreementResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                => success(s)
      case Failure(ex: NotLatestEServiceDescriptor)  => badRequest(ex, logMessage)
      case Failure(ex: DescriptorNotInExpectedState) => badRequest(ex, logMessage)
      case Failure(ex: MissingCertifiedAttributes)   => badRequest(ex, logMessage)
      case Failure(ex: EServiceNotFound)             => badRequest(ex, logMessage)
      case Failure(ex: AgreementAlreadyExists)       => conflict(ex, logMessage)
      case Failure(ex)                               => internalServerError(ex, logMessage)
    }

  def submitAgreementResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                => success(s)
      case Failure(ex: NotLatestEServiceDescriptor)  => badRequest(ex, logMessage)
      case Failure(ex: AgreementNotFound)            => notFound(ex, logMessage)
      case Failure(ex: AgreementNotInExpectedState)  => badRequest(ex, logMessage)
      case Failure(ex: ConsumerWithNotValidEmail)    => badRequest(ex, logMessage)
      case Failure(ex: AgreementSubmissionFailed)    => badRequest(ex, logMessage)
      case Failure(ex: MissingCertifiedAttributes)   => badRequest(ex, logMessage)
      case Failure(ex: DescriptorNotInExpectedState) => badRequest(ex, logMessage)
      case Failure(ex: OperationNotAllowed)          => forbidden(ex, logMessage)
      case Failure(ex: AgreementAlreadyExists)       => conflict(ex, logMessage)
      case Failure(ex)                               => internalServerError(ex, logMessage)
    }

  def activateAgreementResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                => success(s)
      case Failure(ex: NotLatestEServiceDescriptor)  => badRequest(ex, logMessage)
      case Failure(ex: AgreementNotInExpectedState)  => badRequest(ex, logMessage)
      case Failure(ex: AgreementNotFound)            => notFound(ex, logMessage)
      case Failure(ex: AgreementActivationFailed)    => badRequest(ex, logMessage)
      case Failure(ex: DescriptorNotInExpectedState) => badRequest(ex, logMessage)
      case Failure(ex: OperationNotAllowed)          => forbidden(ex, logMessage)
      case Failure(ex: AgreementAlreadyExists)       => conflict(ex, logMessage)
      case Failure(ex)                               => internalServerError(ex, logMessage)
    }

  def rejectAgreementResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                               => success(s)
      case Failure(ex: AgreementNotFound)           => notFound(ex, logMessage)
      case Failure(ex: AgreementNotInExpectedState) => badRequest(ex, logMessage)
      case Failure(ex: OperationNotAllowed)         => forbidden(ex, logMessage)
      case Failure(ex)                              => internalServerError(ex, logMessage)
    }

  def archiveAgreementResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                       => success(s)
      case Failure(ex: AgreementNotFound)   => notFound(ex, logMessage)
      case Failure(ex: OperationNotAllowed) => forbidden(ex, logMessage)
      case Failure(ex)                      => internalServerError(ex, logMessage)
    }

  def suspendAgreementResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                               => success(s)
      case Failure(ex: AgreementNotFound)           => notFound(ex, logMessage)
      case Failure(ex: AgreementNotInExpectedState) => badRequest(ex, logMessage)
      case Failure(ex: OperationNotAllowed)         => forbidden(ex, logMessage)
      case Failure(ex)                              => internalServerError(ex, logMessage)
    }

  def deleteAgreementResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                               => success(s)
      case Failure(ex: AgreementNotFound)           => notFound(ex, logMessage)
      case Failure(ex: AgreementNotInExpectedState) => badRequest(ex, logMessage)
      case Failure(ex: OperationNotAllowed)         => forbidden(ex, logMessage)
      case Failure(ex)                              => internalServerError(ex, logMessage)
    }

  def updateAgreementByIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                               => success(s)
      case Failure(ex: AgreementNotFound)           => notFound(ex, logMessage)
      case Failure(ex: OperationNotAllowed)         => forbidden(ex, logMessage)
      case Failure(ex: AgreementNotInExpectedState) => badRequest(ex, logMessage)
      case Failure(ex)                              => internalServerError(ex, logMessage)
    }

  def upgradeAgreementByIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                               => success(s)
      case Failure(ex: AgreementNotFound)           => notFound(ex, logMessage)
      case Failure(ex: OperationNotAllowed)         => forbidden(ex, logMessage)
      case Failure(ex: AgreementNotInExpectedState) => badRequest(ex, logMessage)
      case Failure(ex: PublishedDescriptorNotFound) => badRequest(ex, logMessage)
      case Failure(ex: NoNewerDescriptorExists)     => badRequest(ex, logMessage)
      case Failure(ex)                              => internalServerError(ex, logMessage)
    }

  def cloneAgreementResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                               => success(s)
      case Failure(ex: AgreementNotFound)           => notFound(ex, logMessage)
      case Failure(ex: OperationNotAllowed)         => forbidden(ex, logMessage)
      case Failure(ex: AgreementNotInExpectedState) => badRequest(ex, logMessage)
      case Failure(ex: MissingCertifiedAttributes)  => badRequest(ex, logMessage)
      case Failure(ex: EServiceNotFound)            => badRequest(ex, logMessage)
      case Failure(ex: AgreementAlreadyExists)      => conflict(ex, logMessage)
      case Failure(ex)                              => internalServerError(ex, logMessage)
    }

  def getAgreementByIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                     => success(s)
      case Failure(ex: AgreementNotFound) => notFound(ex, logMessage)
      case Failure(ex)                    => internalServerError(ex, logMessage)
    }

  def getAgreementsResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)  => success(s)
      case Failure(ex) => internalServerError(ex, logMessage)
    }

  def computeAgreementsByAttributeResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                    => success(s)
      case Failure(ex: TenantIdNotFound) => notFound(ex, logMessage)
      case Failure(ex)                   => internalServerError(ex, logMessage)
    }

  def addAgreementConsumerDocumentResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                             => success(s)
      case Failure(ex: OperationNotAllowed)       => forbidden(ex, logMessage)
      case Failure(ex: DocumentsChangeNotAllowed) => forbidden(ex, logMessage)
      case Failure(ex: AgreementNotFound)         => notFound(ex, logMessage)
      case Failure(ex)                            => internalServerError(ex, logMessage)
    }

  def getAgreementConsumerDocumentResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                    => success(s)
      case Failure(ex: DocumentNotFound) => notFound(ex, logMessage)
      case Failure(ex)                   => internalServerError(ex, logMessage)
    }

  def removeAgreementConsumerDocumentResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                             => success(s)
      case Failure(ex: OperationNotAllowed)       => forbidden(ex, logMessage)
      case Failure(ex: DocumentsChangeNotAllowed) => forbidden(ex, logMessage)
      case Failure(ex: DocumentNotFound)          => notFound(ex, logMessage)
      case Failure(ex)                            => internalServerError(ex, logMessage)
    }

  def getAgreementProducersResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)  => success(s)
      case Failure(ex) => internalServerError(ex, logMessage)
    }

  def getAgreementConsumersResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)  => success(s)
      case Failure(ex) => internalServerError(ex, logMessage)
    }

  def getAgreementEServicesResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)  => success(s)
      case Failure(ex) => internalServerError(ex, logMessage)
    }
}
