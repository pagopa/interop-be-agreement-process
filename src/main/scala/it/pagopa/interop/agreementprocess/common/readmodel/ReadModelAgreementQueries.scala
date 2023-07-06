package it.pagopa.interop.agreementprocess.common.readmodel

import it.pagopa.interop.agreementprocess.common.Adapters._
import it.pagopa.interop.agreementmanagement.model.agreement.{PersistentAgreement, PersistentAgreementState}
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.agreementprocess.model.{AgreementState, CompactEService}
import scala.concurrent.{ExecutionContext, Future}
import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Accumulators.first
import org.mongodb.scala.model.Aggregates.{`match`, count, lookup, project, sort, unwind, addFields, group}
import org.mongodb.scala.model.{Filters, Field, UnwindOptions}
import org.mongodb.scala.model.Projections.{computed, fields, include}
import org.mongodb.scala.model.Sorts.ascending
import it.pagopa.interop.agreementmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.catalogmanagement.{model => CatalogManagement}
import it.pagopa.interop.agreementprocess.model.CompactOrganization
import it.pagopa.interop.agreementprocess.api.impl._
import java.util.UUID

object ReadModelAgreementQueries extends ReadModelQuery {

  def getAgreementById(
    agreementId: UUID
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Option[PersistentAgreement]] = {
    readModel.findOne[PersistentAgreement]("agreements", Filters.eq("data.id", agreementId.toString))
  }

  private def getAgreementsFilters(
    producerId: Option[UUID],
    consumerId: Option[UUID],
    eserviceId: Option[UUID],
    descriptorId: Option[UUID],
    states: Seq[PersistentAgreementState],
    attributeId: Option[UUID]
  ): Bson = {
    val producerIdFilter      = producerId.map(id => Filters.eq("data.producerId", id.toString))
    val consumerIdFilter      = consumerId.map(id => Filters.eq("data.consumerId", id.toString))
    val eserviceIdFilter      = eserviceId.map(id => Filters.eq("data.eserviceId", id.toString))
    val descriptorIdFilter    = descriptorId.map(id => Filters.eq("data.descriptorId", id.toString))
    val agreementStatesFilter = mapToVarArgs(
      states
        .map(_.toString)
        .map(Filters.eq("data.state", _))
    )(Filters.or)
    val attributeIdFilter     = attributeId.map { id =>
      Filters.or(
        Filters.eq("data.certifiedAttributes.id", id.toString),
        Filters.eq("data.declaredAttributes.id", id.toString),
        Filters.eq("data.verifiedAttributes.id", id.toString)
      )
    }

    mapToVarArgs(
      producerIdFilter.toList ++ consumerIdFilter.toList ++ eserviceIdFilter.toList ++ descriptorIdFilter.toList ++ agreementStatesFilter.toList ++ attributeIdFilter.toList
    )(Filters.and)
      .getOrElse(Filters.empty())
  }

  def getAgreements(
    producerId: Option[UUID],
    consumerId: Option[UUID],
    eserviceId: Option[UUID],
    descriptorId: Option[UUID],
    agreementStates: Seq[PersistentAgreementState],
    attributeId: Option[UUID],
    offset: Int,
    limit: Int
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[Seq[PersistentAgreement]] = {
    val filters = getAgreementsFilters(
      producerId = producerId,
      consumerId = consumerId,
      eserviceId = eserviceId,
      descriptorId = descriptorId,
      states = agreementStates,
      attributeId = attributeId
    )
    readModel.find[PersistentAgreement]("agreements", filters, offset, limit)
  }

  def listAgreements(
    eServicesIds: List[String],
    consumersIds: List[String],
    producersIds: List[String],
    descriptorsIds: List[String],
    states: List[AgreementState],
    showOnlyUpgradeable: Boolean,
    offset: Int,
    limit: Int
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PaginatedResult[PersistentAgreement]] = {

    val query: Bson =
      listAgreementsFilters(eServicesIds, consumersIds, producersIds, descriptorsIds, states, showOnlyUpgradeable)

    val eServicesLookupPipeline: Seq[Bson] =
      Seq(`match`(query), lookup("eservices", "data.eserviceId", "data.id", "eservices"), unwind("$eservices"))

    val upgradablePipeline: Seq[Bson] = {
      if (showOnlyUpgradeable) {
        Seq(
          addFields(
            Field(
              "currentDescriptor",
              Document("""{ $filter: {
              input: "$eservices.data.descriptors",
              as: "descr",         
              cond: {  $eq: ["$$descr.id" , "$data.descriptorId"]}}} }""")
            )
          ),
          unwind("$currentDescriptor"),
          addFields(
            Field(
              "upgradableDescriptor",
              Document(
                """{ $filter: {
              input: "$eservices.data.descriptors",
              as: "upgradable",         
              cond: { $and: [
                      {$gt:["$$upgradable.activatedAt", "$currentDescriptor.activatedAt"]}, 
                      {$in:["$$upgradable.state", ['""" + CatalogManagement.Published + """', '""" +
                  CatalogManagement.Suspended + """']]}
                  ]}}} }"""
              )
            )
          ),
          `match`(Filters.exists("upgradableDescriptor.0", true))
        )

      } else
        Seq.empty
    }

    val countPipeline: Seq[Bson] = {
      Seq(count("totalCount"), project(computed("data", Document("""{ "totalCount" : "$totalCount" }"""))))
    }

    for {
      agreements <- readModel.aggregate[PersistentAgreement](
        "agreements",
        eServicesLookupPipeline ++ upgradablePipeline ++
          Seq(
            project(
              fields(
                include("data", "eservices"),
                computed("lowerName", Document("""{ "$toLower" : "$eservices.data.name" }"""))
              )
            ),
            sort(ascending("lowerName"))
          ),
        offset = offset,
        limit = limit
      )
      count      <- readModel.aggregate[TotalCountResult](
        "agreements",
        if (upgradablePipeline.nonEmpty) eServicesLookupPipeline ++ upgradablePipeline ++ countPipeline
        else
          Seq(`match`(query)) ++ countPipeline,
        offset = 0,
        limit = Int.MaxValue
      )

    } yield PaginatedResult(results = agreements, totalCount = count.headOption.map(_.totalCount).getOrElse(0))

  }

  def listAgreementsFilters(
    eServicesIds: List[String],
    consumersIds: List[String],
    producersIds: List[String],
    descriptorsIds: List[String],
    states: List[AgreementState],
    showOnlyUpgradeable: Boolean
  ): Bson = {

    val upgreadableStates = (List(AgreementState.DRAFT, AgreementState.ACTIVE, AgreementState.SUSPENDED))
    val statesForFilter   = states match {
      case Nil if showOnlyUpgradeable  => upgreadableStates
      case list if showOnlyUpgradeable => upgreadableStates.intersect(list)
      case other                       => other
    }

    val statesFilter = listStatesFilter(statesForFilter)

    val eServicesIdsFilter   = mapToVarArgs(eServicesIds.map(Filters.eq("data.eserviceId", _)))(Filters.or)
    val consumersIdsFilter   = mapToVarArgs(consumersIds.map(Filters.eq("data.consumerId", _)))(Filters.or)
    val producersIdsFilter   = mapToVarArgs(producersIds.map(Filters.eq("data.producerId", _)))(Filters.or)
    val descriptorsIdsFilter = mapToVarArgs(descriptorsIds.map(Filters.eq("data.descriptorId", _)))(Filters.or)

    mapToVarArgs(
      eServicesIdsFilter.toList ++ consumersIdsFilter.toList ++ producersIdsFilter.toList ++ descriptorsIdsFilter.toList ++ statesFilter.toList
    )(Filters.and).getOrElse(Filters.empty())
  }

  def listStatesFilter(states: List[AgreementState]): Option[Bson] =
    mapToVarArgs(
      states
        .map(_.toPersistent)
        .map(_.toString)
        .map(Filters.eq("data.state", _))
    )(Filters.or)

  private def listTenantFilters(name: Option[String]): Bson = {
    val nameFilter = name.map(Filters.regex("tenants.data.name", _, "i"))

    mapToVarArgs(nameFilter.toList)(Filters.and).getOrElse(Filters.empty())
  }

  private def listTenantsFilterPipeline(query: Bson, input: String): Seq[Bson] = {

    val placeHolder: String             = s"data.${input}"
    val placeHolderDoubleDollar: String = s"$$data.${input}"

    Seq(
      lookup("tenants", placeHolder, "data.id", "tenants"),
      unwind("$tenants", UnwindOptions().preserveNullAndEmptyArrays(false)),
      `match`(query),
      group(
        Document("""{ "_id":"""" + placeHolderDoubleDollar + """"} """),
        first("tenantId", placeHolderDoubleDollar),
        first("tenantName", "$tenants.data.name")
      )
    )
  }

  def listProducers(name: Option[String], offset: Int, limit: Int)(implicit
    ec: ExecutionContext,
    readModel: ReadModelService
  ): Future[PaginatedResult[CompactOrganization]] = {

    val query: Bson               = listTenantFilters(name)
    val filterPipeline: Seq[Bson] = listTenantsFilterPipeline(query, "producerId")

    for {
      // Using aggregate to perform case insensitive sorting
      //   N.B.: Required because DocumentDB does not support collation
      agreements <- readModel.aggregate[CompactOrganization](
        "agreements",
        filterPipeline ++
          Seq(
            project(
              fields(
                computed("data", Document("""{ "id": "$tenantId", "name": "$tenantName" }""")),
                computed("lowerName", Document("""{ "$toLower" : "$tenantName" }"""))
              )
            ),
            sort(ascending("lowerName"))
          ),
        offset = offset,
        limit = limit
      )
      // Note: This could be obtained using $facet function (avoiding to execute the query twice),
      //   but it is not supported by DocumentDB
      count      <- readModel.aggregate[TotalCountResult](
        "agreements",
        filterPipeline ++
          Seq(count("totalCount"), project(computed("data", Document("""{ "totalCount" : "$totalCount" }""")))),
        offset = 0,
        limit = Int.MaxValue
      )
    } yield PaginatedResult(results = agreements, totalCount = count.headOption.map(_.totalCount).getOrElse(0))
  }

  def listConsumers(name: Option[String], offset: Int, limit: Int)(implicit
    ec: ExecutionContext,
    readModel: ReadModelService
  ): Future[PaginatedResult[CompactOrganization]] = {

    val query: Bson               = listTenantFilters(name)
    val filterPipeline: Seq[Bson] = listTenantsFilterPipeline(query, "consumerId")

    for {
      // Using aggregate to perform case insensitive sorting
      //   N.B.: Required because DocumentDB does not support collation
      agreements <- readModel.aggregate[CompactOrganization](
        "agreements",
        filterPipeline ++
          Seq(
            project(
              fields(
                computed("data", Document("""{ "id": "$tenantId", "name": "$tenantName" }""")),
                computed("lowerName", Document("""{ "$toLower" : "$tenantName" }"""))
              )
            ),
            sort(ascending("lowerName"))
          ),
        offset = offset,
        limit = limit
      )
      // Note: This could be obtained using $facet function (avoiding to execute the query twice),
      //   but it is not supported by DocumentDB
      count      <- readModel.aggregate[TotalCountResult](
        "agreements",
        filterPipeline ++
          Seq(count("totalCount"), project(computed("data", Document("""{ "totalCount" : "$totalCount" }""")))),
        offset = 0,
        limit = Int.MaxValue
      )
    } yield PaginatedResult(results = agreements, totalCount = count.headOption.map(_.totalCount).getOrElse(0))
  }

  private def listEServiceAgreementsFilters(
    name: Option[String],
    consumersIds: List[String],
    producersIds: List[String]
  ): Bson = {
    val nameFilter         = name.map(Filters.regex("eservices.data.name", _, "i"))
    val consumersIdsFilter = mapToVarArgs(consumersIds.map(Filters.eq("data.consumerId", _)))(Filters.or)
    val producersIdsFilter = mapToVarArgs(producersIds.map(Filters.eq("data.producerId", _)))(Filters.or)

    mapToVarArgs(nameFilter.toList ++ consumersIdsFilter.toList ++ producersIdsFilter.toList)(Filters.and)
      .getOrElse(Filters.empty())
  }

  def listEServicesAgreements(
    eServiceName: Option[String],
    consumersIds: List[String],
    producersIds: List[String],
    offset: Int,
    limit: Int
  )(implicit ec: ExecutionContext, readModel: ReadModelService): Future[PaginatedResult[CompactEService]] = {
    val query: Bson               = listEServiceAgreementsFilters(eServiceName, consumersIds, producersIds)
    val filterPipeline: Seq[Bson] = Seq(
      lookup("eservices", "data.eserviceId", "data.id", "eservices"),
      unwind("$eservices", UnwindOptions().preserveNullAndEmptyArrays(false)),
      `match`(query),
      group(
        Document("""{ "_id": "$data.eserviceId" } """),
        first("eserviceId", "$data.eserviceId"),
        first("eserviceName", "$eservices.data.name")
      )
    )

    for {
      // Using aggregate to perform case insensitive sorting
      //   N.B.: Required because DocumentDB does not support collation
      agreements <- readModel.aggregate[CompactEService](
        "agreements",
        filterPipeline ++
          Seq(
            project(
              fields(
                computed("data", Document("""{ "id": "$eserviceId", "name": "$eserviceName" }""")),
                computed("lowerName", Document("""{ "$toLower" : "$eserviceName" }"""))
              )
            ),
            sort(ascending("lowerName"))
          ),
        offset = offset,
        limit = limit
      )
      // Note: This could be obtained using $facet function (avoiding to execute the query twice),
      //   but it is not supported by DocumentDB
      count      <- readModel.aggregate[TotalCountResult](
        "agreements",
        filterPipeline ++
          Seq(count("totalCount"), project(computed("data", Document("""{ "totalCount" : "$totalCount" }""")))),
        offset = 0,
        limit = Int.MaxValue
      )
    } yield PaginatedResult(results = agreements, totalCount = count.headOption.map(_.totalCount).getOrElse(0))
  }
}
