/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sumologic.elasticsearch.restlastic.dsl

trait MappingDsl extends DslCommons {

  sealed trait IndexOption {
    val option: String
  }

  case object KeywordType extends FieldType {
    val rep = "keyword"
  }

  case object TextType extends FieldType {
    val rep = "text"
  }

  // TODO This is because of ES2. We should probably keep the types supported by some versions only separately.
  case object StringType extends FieldType {
    val rep = "string"
  }

  // Numeric datatypes - https://www.elastic.co/guide/en/elasticsearch/reference/current/number.html
  case object LongType extends FieldType {
    val rep = "long"
  }

  case object IntegerType extends FieldType {
    val rep = "integer"
  }

  case object ShortType extends FieldType {
    val rep = "short"
  }

  case object ByteType extends FieldType {
    val rep = "byte"
  }

  case object DoubleType extends FieldType {
    val rep = "double"
  }

  case object FloatType extends FieldType {
    val rep = "float"
  }


  // Date datatype - https://www.elastic.co/guide/en/elasticsearch/reference/current/date.html
  case object DateType extends FieldType {
    val rep = "date"
  }


  // Boolean datatype - https://www.elastic.co/guide/en/elasticsearch/reference/current/boolean.html
  case object BooleanType extends FieldType {
    val rep = "boolean"
  }


  // Binary datatype - https://www.elastic.co/guide/en/elasticsearch/reference/current/binary.html
  case object BinaryType extends FieldType {
    val rep = "binary"
  }

  // Geo point -  https://www.elastic.co/guide/en/elasticsearch/guide/current/geopoints.html
  case object GeoPointType extends FieldType {
    val rep = "geo_point"
  }

  sealed trait IndexType {
    def rep(version: EsVersion): String
  }

  case object NotAnalyzedIndex extends IndexType {
    override def rep(version: EsVersion): String = version match {
      case V2 => "not_analyzed"
      case V6 => "true"
    }
  }

  case object NotIndexedIndex extends IndexType {
    override def rep(version: EsVersion): String = version match {
      case V2 => "no"
      case V6 => "false"
    }
  }

  case object IndexedIndex extends IndexType {
    override def rep(version: EsVersion): String = version match {
      case V2 => ""
      case V6 => "true"
    }
  }

  case object MappingPath {
    val sep = "."

    def createPath(parent: String, child: String) = {
      parent + sep + child
    }
  }

  // Supported in elasticsearch v2.4
  case object DocsIndexOption extends IndexOption {
    val option = "docs"
  }

  // Supported in elasticsearch v2.4
  case object FreqsIndexOption extends IndexOption {
    val option = "freqs"
  }

  // Supported in elasticsearch v2.4
  case object PositionsIndexOption extends IndexOption {
    val option = "positions"
  }

  case object OffsetsIndexOption extends IndexOption {
    val option = "offsets"
  }

  case class Mapping(tpe: Type, mapping: IndexMapping) extends RootObject {
    override def toJson(version: EsVersion): Map[String, Any] = Map(tpe.name -> mapping.toJson(version))
  }

  case class IndexMapping(fields: Map[String, FieldMapping],
                          enableAllFieldOpt: Option[Boolean] = None,
                          strictMapping: Boolean = false)
      extends EsOperation {
    val _all = "_all"
    val _dynamic = "dynamic"
    val _strict = "strict"
    val _enabled = "enabled"

    val dynamicMapping: Map[String, String] = if (strictMapping) {
      Map(_dynamic -> _strict)
    } else {
      Map()
    }

    override def toJson(version: EsVersion): Map[String, Any] = {
      Map(_properties -> fields.mapValues(_.toJson(version))) ++
      dynamicMapping ++
      enableAllFieldOpt.map(f => _all -> Map(_enabled -> f))
    }
  }

  sealed trait FieldMapping extends EsOperation

  val _properties = "properties"
  val _timestamp = "_timestamp"
  val _type = "type"
  val _index = "index"
  val _analyzer = "analyzer"
  val _normalizer = "normalizer"
  val _searchAnalyzer = "search_analyzer"
  val _ignoreAbove = "ignore_above"
  val _fieldIndexOpions = "index_options"
  val _fielddata = "fielddata"

  case class BasicFieldMapping(tpe: FieldType,
                               index: Option[IndexType],
                               analyzer: Option[Name],
                               ignoreAbove: Option[Int] = None,
                               search_analyzer: Option[Name] = None,
                               indexOption: Option[IndexOption] = None,
                               fieldsOption: Option[FieldsMapping] = None,
                               fieldDataOption: Option[Boolean] = None,
                               normalizer: Option[Name] = None)
      extends FieldMapping {

    override def toJson(version: EsVersion): Map[String, Any] =
      Map(_type -> tpe.rep) ++
        index.flatMap { i =>
          val rep = i.rep(version)
          if (rep == "") {
            None
          } else {
            Some(_index -> i.rep(version))
          }
        } ++
        analyzer.map(_analyzer -> _.name) ++
        normalizerMapping(version) ++
        search_analyzer.map(_searchAnalyzer -> _.name) ++
        indexOption.map(_fieldIndexOpions -> _.option) ++
        ignoreAbove.map(_ignoreAbove -> _).toList.toMap ++
        fieldsOption.map(_.toJson(version)).getOrElse(Map[String, Any]()) ++
        fieldDataOption.map(_fielddata -> _)

    private def normalizerMapping(version: EsVersion): Map[String, String] = version match {
      case V2 => Map.empty[String, String] // not supported
      case V6 => normalizer.map { n =>
        _normalizer -> n.name
      }.toMap
    }

    // TODO: ignoreAbove was ignored by mistake, however, in ES 6, it's invalid for Text type (only for Keyword type).
    //   We should probably introduce more type safety to this.
    //   For Keyword type, analyzers are unsupported. Definitely, more type safety would be good to have.
  }

  case class BasicObjectMapping(fields: Map[String, FieldMapping]) extends FieldMapping {
    override def toJson(version: EsVersion): Map[String, Any] = Map(_properties -> fields.mapValues(_.toJson(version)))
  }

  case class FieldsMapping(fields: Map[String, FieldMapping]) extends FieldMapping {
    val _fields = "fields"
    override def toJson(version: EsVersion): Map[String, Any] = Map(_fields -> fields.mapValues(_.toJson(version)))
  }

  case class NestedObjectMapping(fields: Map[String, FieldMapping]) extends FieldMapping {
    val _nested = "nested"
    override def toJson(version: EsVersion): Map[String, Any] = {
      Map(_type -> _nested, _properties -> fields.mapValues(_.toJson(version)))
    }
  }

  trait Completion {
    val _type = "type" -> "completion"
    val _context = "context"
    val _contexts = "contexts"
    val _analyzer = "analyzer" -> analyzer.name
    val _sanalyzer = "search_analyzer" -> analyzer.name

    def analyzer: Name

    def toJson(version: EsVersion): Map[String, Any] = {
      Map(
        _type,
        _analyzer,
        _sanalyzer)
    }
  }

  case class CompletionMapping(context: Map[String, CompletionContext], analyzer: Name = Name("keyword"))
      extends FieldMapping with Completion {

    override def toJson(version: EsVersion): Map[String, Any] = {
      val jsonStr = version match {
        case V6 =>
          Map(_contexts -> context.map {
            case (name, value) =>
              Map(
                "type" -> "category",
                "path" -> value.path,
                "name" -> name
              )
          })
        case V2 =>
          Map(_context -> context.mapValues { value =>
            Map(
              "type" -> "category",
              "path" -> value.path
            )
          }
          )
      }
      super.toJson(version) ++ jsonStr
    }
  }

  case class CompletionMappingWithoutPath(context: Map[String, Unit], analyzer: Name = Name("keyword"))
      extends FieldMapping with Completion {

    override def toJson(version: EsVersion): Map[String, Any] = {
      super.toJson(version) ++
          Map(_contexts -> context.mapValues { case cc =>
            Map("type" -> "category")
          }
          )
    }
  }

  case class CompletionContext(path: String)

  case object NestedFieldMapping extends FieldMapping {
    val _nested = "nested"

    override def toJson(version: EsVersion): Map[String, Any] = Map(_type -> _nested)
  }

}


