package com.ctey.cpmodule.Util;

import com.ctey.cpstatic.Entity.ConnectionEntity;
import com.ctey.cpstatic.Entity.RequestEntity;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.logging.Logger;

import static com.ctey.cpstatic.Static.CPCoreStatic.MAX_POOL_SIZE;

public class MessagePrintUtil {
    private static final Logger LOGGER = Logger.getLogger("ROOT");

    public static void printInfo(String msg) {
        LOGGER.info("[USER INFO] " + msg);
    }

    public static void printSaveTaskStart(String msg) {
        StringBuilder STB = new StringBuilder();
        STB.append("[SAVE TASK] ").append("TASK START:").append(msg);
        LOGGER.info(STB.toString());
    }

    public static void printException(Exception ex) {
        StringBuilder STB = new StringBuilder();
        STB.append("[USER EXCEPTION] ").append(ex.getMessage()).append("\n").append(ExceptionUtils.getStackTrace(ex));
        LOGGER.info(STB.toString());
    }

    public static void printNewConnection(int count, ConnectionEntity conn) {
        StringBuilder STB = new StringBuilder();
        STB.append("[NEW CONNECTION] ").append(count).append("/").append(MAX_POOL_SIZE).append(" UUID:").append(conn.getUUID());
        LOGGER.info(STB.toString());
    }

    public static void printAcquireConnection(int count, ConnectionEntity conn) {
        StringBuilder STB = new StringBuilder();
        STB.append("[ACQUIRE CONNECTION] ").append(count).append("/").append(MAX_POOL_SIZE).append(" UUID:").append(conn.getUUID());
        LOGGER.info(STB.toString());
    }

    public static void printRestoreConnection(int count, ConnectionEntity conn) {
        StringBuilder STB = new StringBuilder();
        STB.append("[RESTORE CONNECTION] ").append(count).append("/").append(MAX_POOL_SIZE).append(" UUID:").append(conn.getUUID());
        LOGGER.info(STB.toString());
    }

    public static void printCloseConnection(ConnectionEntity conn) {
        StringBuilder STB = new StringBuilder();
        STB.append("[CLOSE CONNECTION] ").append(" UUID:").append(conn.getUUID());
        LOGGER.info(STB.toString());
    }

    public static void printRestartConnection(ConnectionEntity conn) {
        StringBuilder STB = new StringBuilder();
        STB.append("[RESTART CONNECTION] ").append(" UUID:").append(conn.getUUID());
        LOGGER.info(STB.toString());
    }

    public static void printRequestArrive(RequestEntity req) {
        StringBuilder STB = new StringBuilder();
        STB.append("[REQUEST ARRIVE] ").append(" UUID:").append(req.getUUID());
        LOGGER.info(STB.toString());
    }

    public static void printRequestAcquire(ConnectionEntity connectionEntity) {
        StringBuilder STB = new StringBuilder();
        STB.append("[REQUEST ACQUIRE] " )
                .append(" REQUEST-UUID:").append(connectionEntity.getRequest().getUUID())
                .append(" CONNECTION-UUID:").append(connectionEntity.getUUID());
        LOGGER.info(STB.toString());
    }

    public static void printRequestWait(RequestEntity req) {
        StringBuilder STB = new StringBuilder();
        STB.append("[REQUEST WAIT] ").append(" UUID:").append(req.getUUID());
        LOGGER.info(STB.toString());
    }

    public static void printRequestRelease(ConnectionEntity conn) {
        StringBuilder STB = new StringBuilder();
        STB.append("[REQUEST RELEASE] ")
                .append(" REQUEST-UUID:").append(conn.getRequest().getUUID())
                .append(" CONNECTION-UUID:").append(conn.getUUID());
        LOGGER.info(STB.toString());
    }

    public static void printRequestTimeout(RequestEntity req) {
        StringBuilder STB = new StringBuilder();
        STB.append("[REQUEST TIMEOUT] ").append(" UUID:").append(req.getUUID());
        LOGGER.info(STB.toString());
    }

    public static void printRequestDisrupt(ConnectionEntity conn) {
        StringBuilder STB = new StringBuilder();
        STB.append("[REQUEST DISRUPT] ")
                .append(" REQUEST-UUID:").append(conn.getRequest().getUUID())
                .append(" CONNECTION-UUID:").append(conn.getUUID());
        LOGGER.info(STB.toString());
    }

}
