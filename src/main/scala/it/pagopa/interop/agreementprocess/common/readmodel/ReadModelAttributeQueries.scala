package it.pagopa.interop.agreementprocess.common.readmodel

import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.JsonFormats._
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import org.mongodb.scala.model.Filters

import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

object ReadModelAttributeQueries extends ReadModelQuery {

  def getAttributeById(
    attributeId: UUID
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Option[PersistentAttribute]] = {
    readModel.findOne[PersistentAttribute]("attributes", Filters.eq("data.id", attributeId.toString))
  }
}
