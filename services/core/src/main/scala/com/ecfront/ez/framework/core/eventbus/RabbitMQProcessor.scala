package com.ecfront.ez.framework.core.eventbus

import com.ecfront.common.{JsonHelper, Resp}
import com.ecfront.ez.framework.core.cluster.RabbitMQClusterManager
import com.ecfront.ez.framework.core.monitor.TaskMonitor
import com.ecfront.ez.framework.core.rpc.RPCProcessor
import com.ecfront.ez.framework.core.{EZ, EZContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

/**
  * 事件总线处理器
  */
class RabbitMQProcessor extends EventBusProcessor {

  private[core] def init(): Resp[Void] = {
    logger.info("[EB] Init successful")
    Resp.success(null)
  }

  override protected def doPublish(address: String, message: Any, args: Map[String, String]): Unit = {
    RabbitMQClusterManager.publish(address, toJsonString(message),
      args + (FLAG_CONTEXT -> JsonHelper.toJsonString(EZ.context)))
  }

  override protected def doRequest(address: String, message: Any, args: Map[String, String], ha: Boolean): Unit = {
    RabbitMQClusterManager.request(address, toJsonString(message),
      args + (FLAG_CONTEXT -> JsonHelper.toJsonString(EZ.context)))
  }

  override protected def doAck[E](address: String, message: Any, args: Map[String, String], timeout: Long)
                                 (implicit e: Manifest[E]): (E, Map[String, String]) = {
    val result = RabbitMQClusterManager.ack(address, toJsonString(message),
      args + (FLAG_CONTEXT -> JsonHelper.toJsonString(EZ.context)), timeout)
    if (result._2.contains(FLAG_CONTEXT)) {
      EZContext.setContext(JsonHelper.toObject[EZContext](result._2(FLAG_CONTEXT)))
    }
    val headers = result._2 - FLAG_CONTEXT
    try {
      val msg =
        if (e == manifest[String]) {
          result._1.asInstanceOf[E]
        } else if (e != manifest[Nothing]) {
          JsonHelper.toObject[E](result._1)
        } else {
          null.asInstanceOf[E]
        }
      (msg, headers)
    } catch {
      case e: Throwable =>
        logger.error(s"[EB] Ack reply a message error : [$address] : ${result._1} ", e.getMessage)
        throw e
    }
  }

  override protected def doAckAsync[E](replyFun: => (E, Map[String, String]) => Unit, replyError: => Throwable => Unit,
                                       address: String, message: Any, args: Map[String, String], timeout: Long)(implicit e: Manifest[E]): Unit = {
    RabbitMQClusterManager.ackAsync(address, toJsonString(message),
      args + (FLAG_CONTEXT -> JsonHelper.toJsonString(EZ.context)), timeout) ({
      (replyMessage, replyArgs) =>
        if (replyArgs.contains(FLAG_CONTEXT)) {
          EZContext.setContext(JsonHelper.toObject[EZContext](replyArgs(FLAG_CONTEXT)))
        }
        val headers = replyArgs - FLAG_CONTEXT
        try {
          val msg =
            if (e == manifest[String]) {
              replyMessage.asInstanceOf[E]
            } else if (e != manifest[Nothing]) {
              JsonHelper.toObject[E](replyMessage)
            } else {
              null.asInstanceOf[E]
            }
          replyFun(msg, headers)
        } catch {
          case e: Throwable =>
            logger.error(s"[EB] Ack reply a message error : [$address] : $message ", e.getMessage)
            throw e
        }
    },replyError)
  }

  override protected def doSubscribe[E: Manifest](address: String, reqClazz: Class[E])(receivedFun: (E, Map[String, String]) => Unit): Unit = {
    RabbitMQClusterManager.subscribe(address) {
      (message, args) =>
        if (args.contains(FLAG_CONTEXT)) {
          EZContext.setContext(JsonHelper.toObject[EZContext](args(FLAG_CONTEXT)))
        }
        val headers = args - FLAG_CONTEXT
        logger.trace(s"[EB] Received a subscribe message [$address] : $headers > ${RPCProcessor.cutPrintShow(message)} ")
        try {
          val msg = toObject[E](message, reqClazz)
          receivedFun(msg, headers)
        } catch {
          case ex: Throwable =>
            logger.error(s"[EB] subscribe [$address] Execute error", ex)
        }
    }
  }

  override protected def doResponse[E: Manifest](address: String, reqClazz: Class[E])(receivedFun: (E, Map[String, String]) => Unit): Unit = {
    RabbitMQClusterManager.response(address) {
      (message, args) =>
        if (args.contains(FLAG_CONTEXT)) {
          EZContext.setContext(JsonHelper.toObject[EZContext](args(FLAG_CONTEXT)))
        }
        val headers = args - FLAG_CONTEXT
        logger.trace(s"[EB] Received a response message [$address] : $headers > ${RPCProcessor.cutPrintShow(message)} ")
        try {
          val msg = toObject[E](message, reqClazz)
          receivedFun(msg, headers)
        } catch {
          case ex: Throwable =>
            logger.error(s"[EB] response [$address] Execute error", ex)
        }
    }
  }

  override protected def doReply[E: Manifest](address: String, reqClazz: Class[E])
                                             (receivedFun: (E, Map[String, String]) => (Any, Map[String, String])): Unit = {
    RabbitMQClusterManager.reply(address) {
      (message, args) =>
        if (args.contains(FLAG_CONTEXT)) {
          EZContext.setContext(JsonHelper.toObject[EZContext](args(FLAG_CONTEXT)))
        }
        val headers = args - FLAG_CONTEXT
        logger.trace(s"[EB] Received a reply message [$address] : $headers > ${RPCProcessor.cutPrintShow(message)} ")
        try {
          val msg = toObject[E](message, reqClazz)
          val result = receivedFun(msg, headers)
          (toJsonString(result._1), result._2 + (FLAG_CONTEXT -> JsonHelper.toJsonString(EZ.context)))
        } catch {
          case ex: Throwable =>
            logger.error(s"[EB] reply [$address] Execute error", ex)
            (toJsonString(Resp.serverError(s"[EB] reply [$address] Execute error : ${ex.getMessage}")), Map(FLAG_CONTEXT -> JsonHelper.toJsonString(EZ.context)))
        }
    }
  }

  override protected def doReplyAsync[E: Manifest](address: String, reqClazz: Class[E])
                                                  (receivedFun: (E, Map[String, String]) => Future[(Any, Map[String, String])]): Unit = {
    RabbitMQClusterManager.replyAsync(address) {
      (message, args) =>
        val p = Promise[(String, Map[String, String])]()
        val tmpContent =
          if (args.contains(FLAG_CONTEXT)) {
            EZContext.setContext(JsonHelper.toObject[EZContext](args(FLAG_CONTEXT)))
            args(FLAG_CONTEXT)
          } else {
            JsonHelper.toJsonString(EZContext.getContext)
          }
        val headers = args - FLAG_CONTEXT
        logger.trace(s"[EB] Received a reply message [$address] : $headers > ${RPCProcessor.cutPrintShow(message)} ")
        try {
          val msg = toObject[E](message, reqClazz)
          receivedFun(msg, headers).onSuccess {
            case result =>
              p.success((toJsonString(result._1), result._2 + (FLAG_CONTEXT -> tmpContent)))
          }
        } catch {
          case ex: Throwable =>
            logger.error(s"[EB] reply [$address] Execute error", ex)
            p.success((toJsonString(Resp.serverError(s"[EB] reply [$address] Execute error : ${ex.getMessage}")), Map(FLAG_CONTEXT -> tmpContent)))
        }
        p.future
    }
  }

  private def toJsonString(obj: Any): String = {
    obj match {
      case o: String => o
      case _ => JsonHelper.toJsonString(obj)
    }
  }

}