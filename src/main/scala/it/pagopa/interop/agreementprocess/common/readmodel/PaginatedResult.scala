package it.pagopa.interop.agreementprocess.common.readmodel

// TODO This should go in commons once the $facet command will be integrated in the aggregate function
final case class PaginatedResult[A](results: Seq[A], totalCount: Int)
