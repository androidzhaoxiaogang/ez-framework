package com.ecfront.ez.framework.core.rpc

import com.ecfront.common._
import com.ecfront.ez.framework.core.EZ
import com.ecfront.ez.framework.core.rpc.Method.Method
import com.typesafe.scalalogging.slf4j.LazyLogging

import scala.reflect.runtime._

object AutoBuildingProcessor extends LazyLogging {

  private val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)

  /**
    * 使用基于注解的自动构建
    *
    * @param rootPackage 服务类所在的根包名
    * @return 当前实例
    */
  def autoBuilding(rootPackage: String): this.type = {
    ClassScanHelper.scan[RPC](rootPackage).foreach {
      clazz =>
        if (clazz.getSimpleName.endsWith("$")) {
          process(runtimeMirror.reflectModule(runtimeMirror.staticModule(clazz.getName)).instance.asInstanceOf[AnyRef])
        } else {
          process(clazz.newInstance().asInstanceOf[AnyRef])
        }
    }
    this
  }

  private def process(instance: AnyRef): Unit = {
    val clazz = instance.getClass
    // 根路径
    var baseUri = BeanHelper.getClassAnnotation[RPC](clazz).get.baseUri
    if (!baseUri.endsWith("/")) {
      baseUri += "/"
    }
    try {
      BeanHelper.findMethodAnnotations(clazz,
        Seq(classOf[GET], classOf[POST], classOf[PUT], classOf[DELETE], classOf[WS], classOf[SUB], classOf[RESP], classOf[REPLY])).foreach {
        methodInfo =>
          val methodMirror = BeanHelper.invoke(instance, methodInfo.method)
          val annInfo = methodInfo.annotation match {
            case ann: GET =>
              (Channel.HTTP, Method.GET, if (ann.uri.startsWith("/")) ann.uri else baseUri + ann.uri, null)
            case ann: POST =>
              (Channel.HTTP, Method.POST, if (ann.uri.startsWith("/")) ann.uri else baseUri + ann.uri, getClassFromMethodInfo(methodInfo))
            case ann: PUT =>
              (Channel.HTTP, Method.PUT, if (ann.uri.startsWith("/")) ann.uri else baseUri + ann.uri, getClassFromMethodInfo(methodInfo))
            case ann: DELETE =>
              (Channel.HTTP, Method.DELETE, if (ann.uri.startsWith("/")) ann.uri else baseUri + ann.uri, null)
            case ann: WS =>
              (Channel.WS, Method.WS, if (ann.uri.startsWith("/")) ann.uri else baseUri + ann.uri, getClassFromMethodInfo(methodInfo))
            case ann: SUB =>
              (Channel.EB, Method.PUB_SUB, if (ann.uri.startsWith("/")) ann.uri else baseUri + ann.uri, getClassFromMethodInfo(methodInfo))
            case ann: RESP =>
              (Channel.EB, Method.REQ_RESP, if (ann.uri.startsWith("/")) ann.uri else baseUri + ann.uri, getClassFromMethodInfo(methodInfo))
            case ann: REPLY =>
              (Channel.EB, Method.REPLY, if (ann.uri.startsWith("/")) ann.uri else baseUri + ann.uri, getClassFromMethodInfo(methodInfo))
          }
          RPCProcessor.add(annInfo._1, annInfo._2, annInfo._3, annInfo._4, fun(annInfo._2, methodMirror))
      }
    } catch {
      case e: Throwable =>
        logger.error(s"${instance.getClass} Method reflect error")
        throw e
    }
  }

  private def fun(method: Method, methodMirror: universe.MethodMirror): (Map[String, String], Any) => Resp[Any] = {
    (parameter, body) =>
      try {
        if (method == Method.GET || method == Method.DELETE) {
          methodMirror(parameter).asInstanceOf[Resp[Any]]
        } else {
          methodMirror(parameter, body).asInstanceOf[Resp[Any]]
        }
      } catch {
        case e: Exception =>
          val context = EZ.context
          logger.error(s"Occurred unchecked exception by ${context.id}:${context.sourceRPCPath} from ${context.sourceIP}", e)
          Resp.serverError(s"Occurred unchecked exception by ${context.id}:${context.sourceRPCPath} from ${context.sourceIP} : ${e.getMessage}")
      }
  }

  private def getClassFromMethodInfo(methodInfo: methodAnnotationInfo): Class[_] = {
    BeanHelper.getClassByStr(methodInfo.method.paramLists.head(1).info.toString)
  }

}