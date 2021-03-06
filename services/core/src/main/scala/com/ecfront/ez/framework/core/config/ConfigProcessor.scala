package com.ecfront.ez.framework.core.config

import java.io.File

import com.ecfront.common.{JsonHelper, Resp}
import com.ecfront.ez.framework.core.EZ
import com.ecfront.ez.framework.core.logger.Logging

import scala.collection.JavaConversions._
import scala.io.Source

object ConfigProcessor extends Logging {

  /**
    * 解析服务配置 , 默认情况下加载classpath根路径下的`ez.json`文件
    *
    * @param specialConfig 使用自定义配置内容（json格式）
    * @return 服务配置
    */
  private[core] def init(specialConfig: String = null): Resp[EZConfig] = {
    try {
      val configContent =
        if (specialConfig == null) {
          Source.fromFile(EZ.Info.confPath + "ez.json", "UTF-8").mkString
        } else {
          specialConfig
        }
      val ezConfig =
        if (configContent.startsWith("@")) {
          // 统一配置
          val Array(app, module, path) = configContent.substring(1).split("#")
          var unifyConfigPath = if (path.endsWith("/")) path else path + "/"
          if (unifyConfigPath.startsWith(".")) {
            unifyConfigPath = this.getClass.getResource("/").getPath + unifyConfigPath
          }
          logger.info("[Config] load unify config path :" + unifyConfigPath)
          val basicConfig = parseConfig(Source.fromFile(new File(unifyConfigPath + "ez.json"), "UTF-8").mkString)
          val moduleConfig = parseConfig(Source.fromFile(unifyConfigPath + s"ez.$app.$module.json", "UTF-8").mkString)
          moduleConfig.ez.app = app
          moduleConfig.ez.module = module
          moduleConfig.ez.instance = moduleConfig.ez.instance + System.nanoTime()
          if (moduleConfig.ez.cluster == null) {
            moduleConfig.ez.cluster = basicConfig.ez.cluster
          }
          if (moduleConfig.ez.rpc == null) {
            moduleConfig.ez.rpc = basicConfig.ez.rpc
          }
          if (moduleConfig.ez.timezone == null) {
            moduleConfig.ez.timezone = basicConfig.ez.timezone
          }
          if (moduleConfig.ez.language == null) {
            moduleConfig.ez.language = basicConfig.ez.language
          }
          moduleConfig.ez.isDebug = basicConfig.ez.isDebug
          if (moduleConfig.ez.perf == null || moduleConfig.ez.perf.isEmpty) {
            moduleConfig.ez.perf = basicConfig.ez.perf
          }
          // 服务处理
          moduleConfig.ez.services =
            moduleConfig.ez.services.map {
              service =>
                if (service._2 == null) {
                  // 使用基础配置
                  service._1 -> basicConfig.ez.services(service._1)
                } else {
                  service
                }
            }
          // 全局参数
          if (basicConfig.args.size() != 0) {
            val args = JsonHelper.createObjectNode()
            basicConfig.args.fields().foreach {
              arg =>
                args.set(arg.getKey, arg.getValue)
            }
            moduleConfig.args.fields().foreach {
              arg =>
                args.set(arg.getKey, arg.getValue)
            }
            moduleConfig.args = JsonHelper.toJson(args)
          }
          moduleConfig
        } else {
          // 普通配置
          parseConfig(configContent)
        }
      Resp.success(ezConfig)
    } catch {
      case e: Throwable =>
        logger.error("[Config ] config parse error :" + e.getMessage, e)
        throw e
    }
  }


  private def parseConfig(configContent: String): EZConfig = {
    val ezConfig = JsonHelper.toObject(configContent, classOf[EZConfig])
    if (ezConfig.ez.instance == null) {
      ezConfig.ez.instance = (EZ.Info.projectIp + EZ.Info.projectPath).hashCode + ""
    }
    if (ezConfig.ez.language == null) {
      ezConfig.ez.language = "en"
    }
    if (ezConfig.ez.perf == null) {
      ezConfig.ez.perf = collection.mutable.Map[String, Any]()
    }
    ezConfig
  }
}
