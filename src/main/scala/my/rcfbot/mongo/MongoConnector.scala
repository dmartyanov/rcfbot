package my.rcfbot.mongo

import my.rcfbot.domain.JsonModel
import my.rcfbot.util.{ConfigHelper, ExecutionContextHelper, LoggerHelper}
import reactivemongo.api.MongoDriver
import reactivemongo.api.Cursor
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson._
import spray.json._

import scala.concurrent.Future

/**
  * Created by dmartyanov on 11/9/16.
  */
trait MongoConnector[T <: JsonModel] {
  this: ExecutionContextHelper with ConfigHelper with LoggerHelper =>

  lazy val driver = new MongoDriver()
  lazy val dbName = conf.get[String]("mongo.db", "rcfbot")
  lazy val port = conf.get[Int]("mongo.port", 27327)

  lazy val nodes = conf.get[List[String]]("mongo.nodes", List("127.0.0.1"))

  lazy val connection = driver.connection(nodes)

  lazy val db = connection.database(dbName)

  def colName: String

  def notInsertedExceptionText(obj: T): String

  def notFoundExceptionText(id: String): String

  lazy val collection = db.map(db => db.apply[BSONCollection](colName))

  def idFieldFrom(name: String): String = if (name.equals("_id")) "id" else name

  def idFieldTo(name: String): String = if (name.equals("id")) "_id" else name

  def fromBson(doc: BSONDocument)(implicit f: RootJsonFormat[T]): T = f.read(fromBSON(doc))

  def toBson(obj: T)(implicit f: RootJsonFormat[T]): BSONValue = toBSON(f.write(obj))


  def insertDocument(obj: T)(implicit f: RootJsonFormat[T]): Future[T] =
    collection
      .flatMap(c => c.insert(toBson(obj).asInstanceOf[BSONDocument]))
      .map(r =>
        if (r.ok) obj
        else throw new DocumentProcessingException(notInsertedExceptionText(obj) +
          s": cause [${r.writeConcernError.map(_.errmsg).getOrElse("unknown error")}]")
      )

  def upsertDocument(obj: T)(implicit f: RootJsonFormat[T]): Future[T] =
    collection.flatMap { c =>
      c.update(
        BSONDocument("_id" -> obj.id),
        toBSON(f.write(obj)).asInstanceOf[BSONDocument],
        upsert = true, multi=false
      )
    } map {
      case writeResult if writeResult.ok  =>
        obj
      case _ =>
        throw new RuntimeException(s"Document [${obj.id}] was not upserted")
    }

  val nonDeletedFilter = BSONDocument("deleted" -> true)

  def findById(id: String, m: BSONDocument => BSONDocument = d => d)(implicit f: RootJsonFormat[T]): Future[T] =
    collection.flatMap {
        c => c
          .find(m(BSONDocument("_id" -> id)), Option.empty)
          .cursor[BSONDocument]()
          .collect[List](1, Cursor.FailOnError[List[BSONDocument]]())
      }.map(_.headOption.getOrElse(throw new DocumentProcessingException(notFoundExceptionText(id))))
      .map(fromBson)

  def removeDocument(id: String)(implicit f: RootJsonFormat[T]) =
      collection.flatMap {
        c => c.delete().one(BSONDocument("_id" -> id))
      } map {
        wr => wr.ok
      }

  def removeDoc(id: String) =
    collection.flatMap {
      c => c.remove(BSONDocument("_id" -> id))
    }

  def findByFieldIN(field: String, vs: Seq[String])(implicit f: RootJsonFormat[T]) =
    collection.flatMap {
        c => c
          .find(BSONDocument(field -> BSONDocument("$in" -> vs)), Option.empty)
          .cursor[BSONDocument]()
          .collect[List](-1, Cursor.FailOnError[List[BSONDocument]]())
      }.map(ds => ds.map(fromBson))

  def findByStringField(field: String, v: String)(implicit f: RootJsonFormat[T]) =
    collection.flatMap {
      c => c
        .find(BSONDocument(field -> v), Option.empty)
        .cursor[BSONDocument]()
        .collect[List](-1, Cursor.FailOnError[List[BSONDocument]]())
    }.map(ds => ds.map(fromBson))

  def findAll()(implicit f: RootJsonFormat[T]): Future[List[T]] =
    collection.flatMap {
        c => c
          .find(BSONDocument.empty, Option.empty)
          .cursor[BSONDocument]()
          .collect[List](-1, Cursor.FailOnError[List[BSONDocument]]())
    }.map(ds => ds.map(fromBson))

  def findCustom(selector: BSONDocument)(implicit f: RootJsonFormat[T]) =
    collection
      .flatMap {
        c => c
          .find(selector, Option.empty)
          .cursor[BSONDocument]()
          .collect[List](-1, Cursor.FailOnError[List[BSONDocument]]())
      }.map(ds => ds.map(fromBson))

  def countAggregation[U <: JsonModel](id: String, result: String)(implicit f: RootJsonFormat[U]) =
    collection.flatMap { c =>
      import c.BatchCommands.AggregationFramework.{ Group, SumAll }

      c.aggregatorContext[BSONDocument](Group(BSONString(id))(result -> SumAll))
        .prepared.cursor
        //        Group(groupId)(key -> gf)
        .collect[List](-1, Cursor.FailOnError[List[BSONDocument]]())
    }.map(ds => ds.map(d => f.read(fromBSON(d))))

  def toBSON(json: JsValue): BSONValue = json match {
    case JsString(value) => BSONString(value)
    case JsNumber(num) =>
      if (num.isValidInt) BSONInteger(num.intValue)
      else if (num.isValidLong) BSONLong(num.longValue)
      else BSONDouble(num.doubleValue)
    case JsFalse => BSONBoolean(false)
    case JsTrue => BSONBoolean(true)
    case JsNull => BSONNull
    case JsArray(elems) => BSONArray(elems.map(toBSON))
    case JsObject(fields) => BSONDocument(fields.toList.map(entry => idFieldTo(entry._1) -> toBSON(entry._2)))
  }

  def fromBSON(bson: BSONValue): JsValue = bson match {
    case BSONString(value) => JsString(value)
    case BSONDouble(value) => JsNumber(value)
    case BSONInteger(value) => JsNumber(value)
    case BSONLong(value) => JsNumber(value)
    case BSONBoolean(value) => JsBoolean(value)
    case BSONNull => JsNull
    case arr: BSONArray => JsArray(arr.values.map(fromBSON).toVector)
    case bsonDoc: BSONDocument => JsObject(bsonDoc.elements.map {
      elem => idFieldFrom(elem.name) -> fromBSON(elem.value)
    }.toMap
    )
  }

  def lookupErrorTplt(objTitle: String, id: String) =
    s"$objTitle with id = [$id] was not found in $colName"

  def insertionErrorTplt(objTitle: String, id: String) =
    s"$objTitle with id = [$id] was not inserted in $colName"
}

class DocumentProcessingException(val message: String, val cause: Option[Throwable] = None)
  extends RuntimeException(message, cause.orNull)
