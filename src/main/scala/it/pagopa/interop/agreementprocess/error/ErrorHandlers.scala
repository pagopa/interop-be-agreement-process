package it.pagopa.interop.agreementprocess.error

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.StandardRoute
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.agreementprocess.api.impl.problemOf
import it.pagopa.interop.agreementprocess.error.AgreementProcessErrors.{
  AgreementActivationFailed,
  AgreementAlreadyExists,
  AgreementNotFound,
  AgreementNotInExpectedState,
  DescriptorNotInExpectedState,
  EServiceNotFound,
  MissingCertifiedAttributes,
  MissingDeclaredAttributes,
  NoNewerDescriptorExists,
  OperationNotAllowed,
  PublishedDescriptorNotFound,
  UnexpectedError
}
import it.pagopa.interop.agreementprocess.model.Problem
import it.pagopa.interop.commons.logging.ContextFieldsToLog
import it.pagopa.interop.commons.utils.errors.ComponentError

import scala.util.{Failure, Try}

object ErrorHandlers {

  private def log(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], Try[_]] = {
    case res @ Failure(ex) =>
      logger.error(logMessage, ex)
      res
    case other             => other
  }

  private def completeWithError(statusCode: StatusCode, error: ComponentError)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): StandardRoute = complete(statusCode.intValue, problemOf(statusCode, error))

  private def badRequest(error: ComponentError)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): StandardRoute = completeWithError(StatusCodes.BadRequest, error)

  private def notFound(error: ComponentError)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): StandardRoute = completeWithError(StatusCodes.NotFound, error)

  private def forbidden(error: ComponentError)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): StandardRoute = completeWithError(StatusCodes.Forbidden, error)

  private def internalServerError(
    errorMessage: String
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): StandardRoute = {
    val statusCode = StatusCodes.InternalServerError
    complete(statusCode.intValue, problemOf(statusCode, UnexpectedError(errorMessage)))
  }

  def handleCreationError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): PartialFunction[Try[_], StandardRoute] = log(logMessage) andThen {
    case Failure(ex: DescriptorNotInExpectedState) => badRequest(ex)
    case Failure(ex: AgreementAlreadyExists)       => badRequest(ex)
    case Failure(ex: MissingCertifiedAttributes)   => badRequest(ex)
    case Failure(ex: EServiceNotFound)             => badRequest(ex)
    case Failure(_)                                => internalServerError(logMessage)
  }

  def handleSubmissionError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): PartialFunction[Try[_], StandardRoute] = log(logMessage) andThen {
    case Failure(ex: AgreementNotFound)            => notFound(ex)
    case Failure(ex: AgreementAlreadyExists)       => badRequest(ex)
    case Failure(ex: AgreementNotInExpectedState)  => badRequest(ex)
    case Failure(ex: MissingCertifiedAttributes)   => badRequest(ex)
    case Failure(ex: MissingDeclaredAttributes)    => badRequest(ex)
    case Failure(ex: DescriptorNotInExpectedState) => badRequest(ex)
    case Failure(ex: OperationNotAllowed)          => forbidden(ex)
    case Failure(_)                                => internalServerError(logMessage)
  }

  def handleActivationError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): PartialFunction[Try[_], StandardRoute] = log(logMessage) andThen {
    case Failure(ex: AgreementNotFound)            => notFound(ex)
    case Failure(ex: AgreementAlreadyExists)       => badRequest(ex)
    case Failure(ex: AgreementActivationFailed)    => badRequest(ex)
    case Failure(ex: AgreementNotInExpectedState)  => badRequest(ex)
    case Failure(ex: OperationNotAllowed)          => forbidden(ex)
    case Failure(ex: DescriptorNotInExpectedState) => badRequest(ex)
    case Failure(_)                                => internalServerError(logMessage)
  }

  def handleSuspensionError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): PartialFunction[Try[_], StandardRoute] = log(logMessage) andThen {
    case Failure(ex: AgreementNotFound)           => notFound(ex)
    case Failure(ex: AgreementNotInExpectedState) => badRequest(ex)
    case Failure(ex: OperationNotAllowed)         => forbidden(ex)
    case Failure(_)                               => internalServerError(logMessage)
  }

  def handleUpgradeError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): PartialFunction[Try[_], StandardRoute] = log(logMessage) andThen {
    case Failure(ex: AgreementNotFound)           => notFound(ex)
    case Failure(ex: OperationNotAllowed)         => forbidden(ex)
    case Failure(ex: AgreementNotInExpectedState) => badRequest(ex)
    case Failure(ex: PublishedDescriptorNotFound) => badRequest(ex)
    case Failure(ex: NoNewerDescriptorExists)     => badRequest(ex)
    case Failure(_)                               => internalServerError(logMessage)
  }
}
