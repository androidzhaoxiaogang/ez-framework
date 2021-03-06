package com.ecfront.ez.framework.service.jdbc.scaffold

import java.lang.reflect.ParameterizedType
import java.util.regex.Pattern

import com.ecfront.common.{JsonHelper, Resp}
import com.ecfront.ez.framework.core.logger.Logging
import com.ecfront.ez.framework.core.rpc.{DELETE, GET, POST, PUT}
import com.ecfront.ez.framework.service.jdbc._

/**
  * 简单RPC服务
  *
  * @tparam M 对应的实体类型
  */
trait SimpleRPCService[M <: BaseModel] extends Logging {

  // 持久化对象
  protected val storageObj: BaseStorage[M]

  protected val modelClazz = this.getClass.getGenericInterfaces()(0).asInstanceOf[ParameterizedType].getActualTypeArguments()(0).asInstanceOf[Class[M]]

  protected val modelObj = modelClazz.newInstance()

  val DEFAULT_PAGE_SIZE: Int = 10

  /**
    * 保存操作
    *
    * @param parameter 请求参数
    * @param body      原始（字符串类型）请求体
    * @return 保存结果
    */
  @POST("", "保存", "", "", "")
  def rpcSave(parameter: Map[String, String], body: String): Resp[M] = {
    logger.trace(s"RPC simple save : $body")
    val model = JsonHelper.toObject(body, modelClazz)
    storageObj.save(model)
  }

  /**
    * 更新操作
    *
    * @param parameter 请求参数，必须包含`id`
    * @param body      原始（字符串类型）请求体
    * @return 更新结果
    */
  @PUT(":id/", "更新", "", "", "")
  def rpcUpdate(parameter: Map[String, String], body: String): Resp[M] = {
    if (!parameter.contains("id")) {
      logger.warn(s"【id】not null")
      Resp.badRequest("【id】not null")
    } else {
      logger.trace(s"RPC simple update : $body")
      val model = JsonHelper.toObject(body, modelClazz)
      storageObj.setIdValue(parameter("id"), model)
      storageObj.update(model)
    }
  }

  /**
    * 更新操作
    *
    * @param parameter 请求参数，必须包含`id`
    * @param body      原始（字符串类型）请求体
    * @return 更新结果
    */
  @PUT("uuid/:uuid/", "根据业务主键更新", "", "", "")
  def rpcUpdateByUUID(parameter: Map[String, String], body: String): Resp[M] = {
    if (!parameter.contains("uuid")) {
      logger.warn(s"【uuid】not null")
      Resp.badRequest("【uuid】not null")
    } else {
      logger.trace(s"RPC simple update : $body")
      val model = JsonHelper.toObject(body, modelClazz)
      storageObj.setUUIDValue(parameter("uuid"), model)
      storageObj.update(model)
    }
  }

  /**
    * 查找启用记录操作，对应的实体必须继承[[com.ecfront.ez.framework.service.jdbc.StatusModel]]
    *
    * @param parameter 请求参数，可以包含`condition` 用于筛选条件
    * @return 查找到的结果
    */
  @GET("enable/", "查询启用的记录", "TIP: url参数`condition`表示筛选条件，限制性sql形式", "")
  def rpcFindEnable(parameter: Map[String, String]): Resp[List[M]] = {
    logger.trace(s"RPC simple find enable : $parameter")
    if (classOf[StatusModel].isAssignableFrom(modelClazz)) {
      val conditionR = if (parameter.contains("condition")) conditionCheck(parameter("condition")) else Resp.success("")
      if (conditionR) {
        storageObj.asInstanceOf[StatusStorage[_]].findEnabled(conditionR.body, List()).asInstanceOf[Resp[List[M]]]
      } else {
        conditionR
      }
    } else {
      Resp.notImplemented("")
    }
  }

  /**
    * 查找操作
    *
    * @param parameter 请求参数，可以包含`condition` 用于筛选条件
    * @return 查找到的结果
    */
  @GET("", "查询所有记录", "TIP: url参数`condition`表示筛选条件，限制性sql形式", "")
  def rpcFind(parameter: Map[String, String]): Resp[List[M]] = {
    logger.trace(s"RPC simple find : $parameter")
    val conditionR = if (parameter.contains("condition")) conditionCheck(parameter("condition")) else Resp.success("")
    if (conditionR) {
      storageObj.find(conditionR.body, List())
    } else {
      conditionR
    }
  }

  /**
    * 分页操作
    *
    * @param parameter 请求参数，可以包含
    *                  `condition` 用于筛选条件，
    *                  `pageNumber` 用于设定当前页码，页码从1开始，
    *                  `pageSize` 用于设定每页显示记录数
    * @return 查找到的结果
    */
  @GET("page/:pageNumber/:pageSize/", "分页查询记录", "TIP: url参数`pageNumber`表示当前页，从1开始，`pageSize`表示每页条数，`condition`表示筛选条件，限制性sql形式", "")
  def rpcPage(parameter: Map[String, String]): Resp[Page[M]] = {
    logger.trace(s"RPC simple page : $parameter")
    val pageNumber = if (parameter.contains("pageNumber")) parameter("pageNumber").toLong else 1L
    val pageSize = if (parameter.contains("pageSize")) parameter("pageSize").toInt else DEFAULT_PAGE_SIZE
    val conditionR = if (parameter.contains("condition")) conditionCheck(parameter("condition")) else Resp.success("")
    if (conditionR) {
      storageObj.page(conditionR.body, List(), pageNumber, pageSize)
    } else {
      conditionR
    }
  }

  /**
    * 启用记录分页操作，对应的实体必须继承[[StatusModel]]
    *
    * @param parameter 请求参数，可以包含
    *                  `condition` 用于筛选条件，
    *                  `pageNumber` 用于设定当前页码，页码从1开始，
    *                  `pageSize` 用于设定每页显示记录数
    * @return 查找到的结果
    */
  @GET("enable/page/:pageNumber/:pageSize/", "分页查询启用的记录", "TIP: url参数`pageNumber`表示当前页，从1开始，`pageSize`表示每页条数，`condition`表示筛选条件，限制性sql形式", "")
  def rpcPageEnable(parameter: Map[String, String]): Resp[Page[M]] = {
    logger.trace(s"RPC simple page : $parameter")
    val pageNumber = if (parameter.contains("pageNumber")) parameter("pageNumber").toLong else 1L
    val pageSize = if (parameter.contains("pageSize")) parameter("pageSize").toInt else DEFAULT_PAGE_SIZE
    val conditionR = if (parameter.contains("condition")) conditionCheck(parameter("condition")) else Resp.success("")
    if (conditionR) {
      storageObj.asInstanceOf[StatusStorage[_]].pageEnabled(conditionR.body, List(), pageNumber, pageSize).asInstanceOf[Resp[Page[M]]]
    } else {
      conditionR
    }
  }

  /**
    * 获取单条记录操作
    *
    * @param parameter 请求参数，必须包含`id`
    * @return 获取的结果
    */
  @GET(":id/", "获取一条记录", "", "")
  def rpcGet(parameter: Map[String, String]): Resp[M] = {
    if (!parameter.contains("id")) {
      logger.warn(s"【id】not null")
      Resp.badRequest("【id】not null")
    } else {
      val id = parameter("id")
      logger.trace(s"RPC simple get : $id")
      storageObj.getById(id)
    }
  }

  /**
    * 获取单条记录操作
    *
    * @param parameter 请求参数，必须包含`id`
    * @return 获取的结果
    */
  @GET("uuid/:uuid/", "根据业务主键获取一条记录", "", "")
  def rpcGetByUUID(parameter: Map[String, String]): Resp[M] = {
    if (!parameter.contains("uuid")) {
      logger.warn(s"【uuid】not null")
      Resp.badRequest("【uuid】not null")
    } else {
      val uuid = parameter("uuid")
      logger.trace(s"RPC simple getByUUID : $uuid")
      storageObj.getByUUID(uuid)
    }
  }

  /**
    * 删除单条记录操作
    *
    * @param parameter 请求参数，必须包含`id`
    * @return 删除的结果
    */
  @DELETE(":id/", "删除一条记录", "", "")
  def rpcDelete(parameter: Map[String, String]): Resp[Void] = {
    if (!parameter.contains("id")) {
      logger.warn(s"【id】not null")
      Resp.badRequest("【id】not null")
    } else {
      val id = parameter("id")
      logger.trace(s"RPC simple delete : $id")
      storageObj.deleteById(id)
    }
  }

  /**
    * 删除单条记录操作
    *
    * @param parameter 请求参数，必须包含`id`
    * @return 删除的结果
    */
  @DELETE("uuid/:uuid/", "根据业务主键删除一条记录", "", "")
  def rpcDeleteByUUID(parameter: Map[String, String]): Resp[Void] = {
    if (!parameter.contains("uuid")) {
      logger.warn(s"【uuid】not null")
      Resp.badRequest("【uuid】not null")
    } else {
      val uuid = parameter("uuid")
      logger.trace(s"RPC simple deleteByUUID : $uuid")
      storageObj.deleteByUUID(uuid)
    }
  }

  /**
    * 启用单条记录操作
    *
    * @param parameter 请求参数，必须包含`id`，对应的实体必须继承[[StatusModel]]
    * @return 启用的结果
    */
  @GET(":id/enable/", "启用一条记录", "", "")
  def rpcEnable(parameter: Map[String, String]): Resp[Void] = {
    if (!parameter.contains("id")) {
      logger.warn(s"【id】not null")
      Resp.badRequest("【id】not null")
    } else {
      if (classOf[StatusModel].isAssignableFrom(modelClazz)) {
        val id = parameter("id")
        logger.trace(s"RPC simple enable : $id")
        storageObj.asInstanceOf[StatusStorage[_]].enableById(id)
      } else {
        Resp.notImplemented("")
      }
    }
  }

  /**
    * 启用单条记录操作
    *
    * @param parameter 请求参数，必须包含`id`，对应的实体必须继承[[StatusModel]]
    * @return 启用的结果
    */
  @GET("uuid/:uuid/enable/", "根据业务主键启用一条记录", "", "")
  def rpcEnableByUUID(parameter: Map[String, String]): Resp[Void] = {
    if (!parameter.contains("uuid")) {
      logger.warn(s"【uuid】not null")
      Resp.badRequest("【uuid】not null")
    } else {
      if (classOf[StatusModel].isAssignableFrom(modelClazz)) {
        val uuid = parameter("uuid")
        logger.trace(s"RPC simple enableByUUID : $uuid")
        storageObj.asInstanceOf[StatusStorage[_]].enableByUUID(uuid)
      } else {
        Resp.notImplemented("")
      }
    }
  }

  /**
    * 禁用单条记录操作
    *
    * @param parameter 请求参数，必须包含`id`，对应的实体必须继承[[StatusModel]]
    * @return 禁用的结果
    */
  @GET(":id/disable/", "禁用一条记录", "", "")
  def rpcDisable(parameter: Map[String, String]): Resp[Void] = {
    if (!parameter.contains("id")) {
      logger.warn(s"【id】not null")
      Resp.badRequest("【id】not null")
    } else {
      if (classOf[StatusModel].isAssignableFrom(modelClazz)) {
        val id = parameter("id")
        logger.trace(s"RPC simple enable : $id")
        storageObj.asInstanceOf[StatusStorage[_]].disableById(id)
      } else {
        Resp.notImplemented("")
      }
    }
  }

  /**
    * 禁用单条记录操作
    *
    * @param parameter 请求参数，必须包含`id`，对应的实体必须继承[[StatusModel]]
    * @return 禁用的结果
    */
  @GET("uuid/:uuid/disable/", "根据业务主键禁用一条记录", "", "")
  def rpcDisableByUUID(parameter: Map[String, String]): Resp[Void] = {
    if (!parameter.contains("uuid")) {
      logger.warn(s"【uuid】not null")
      Resp.badRequest("【uuid】not null")
    } else {
      if (classOf[StatusModel].isAssignableFrom(modelClazz)) {
        val uuid = parameter("uuid")
        logger.trace(s"RPC simple enableByUUID : $uuid")
        storageObj.asInstanceOf[StatusStorage[_]].disableByUUID(uuid)
      } else {
        Resp.notImplemented("")
      }
    }
  }

  private val reg = "(?:;)|(?:--)|(/\\*(?:.|[\\n\\r])*?\\*/)|(\\b(select|update|or|delete|insert|trancate|char|into|substr|ascii|declare|exec|count|master|into|drop|execute)\\b)"
  private val sqlPattern = Pattern.compile(reg, Pattern.CASE_INSENSITIVE)

  def conditionCheck(condition: String): Resp[String] = {
    if (sqlPattern.matcher(condition).find()) {
      logger.warn(s"condition illegal")
      Resp.badRequest("condition illegal")
    } else {
      Resp.success(condition)
    }
  }

}
