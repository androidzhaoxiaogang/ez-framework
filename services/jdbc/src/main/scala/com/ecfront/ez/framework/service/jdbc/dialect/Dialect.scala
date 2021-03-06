package com.ecfront.ez.framework.service.jdbc.dialect

import java.sql.SQLException

import com.ecfront.ez.framework.core.logger.Logging
import com.ecfront.ez.framework.service.jdbc.dialect.DialectType.DialectType

trait Dialect {

  def paging(sql: String, pageNumber: Long, pageSize: Long): String

  def count(sql: String): String

  def getTableInfo(tableName: String): String

  def getDriver: String

  def getDialectType: DialectType

  def createTableIfNotExist(tableName: String, tableDesc: String, fields: List[FiledInfo],
                            indexFields: List[String], uniqueFields: List[String], pkField: String): String

  def changeTableName(oriTableName: String, newTableName: String): String

}

object DialectType extends Enumeration {
  type DialectType = Value
  val MYSQL, POSTGRE, H2 = Value
}

case class FiledInfo(name: String, dType: String, desc: String, len: Int, scale: Int)

object DialectFactory extends Logging {

  def parseDialect(url: String): Dialect = {
    url match {
      case u if u.startsWith("jdbc:h2") => H2Dialect
      case u if u.startsWith("jdbc:mysql") => MySQLDialect
      case u if u.startsWith("jdbc:postgresql") => PostgresDialect
      case _ =>
        throw new SQLException(s"Not support url : $url")
    }
  }

}