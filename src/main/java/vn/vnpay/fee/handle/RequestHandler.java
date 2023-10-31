package vn.vnpay.fee.handle;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.vnpay.fee.bean.FeeCommand;
import vn.vnpay.fee.common.CommonUtil;
import vn.vnpay.fee.common.GeneralResponse;
import vn.vnpay.fee.common.HttpStatus;
import vn.vnpay.fee.common.RequestMethod;
import vn.vnpay.fee.service.TransactionService;
import vn.vnpay.fee.service.impl.TransactionServiceImpl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;


public class RequestHandler implements HttpHandler {
    public static final ThreadLocal<String> logIdThreadLocal = new ThreadLocal<>();
    private final Logger logger = LoggerFactory.getLogger(RequestHandler.class);


    @Override
    public void handle(HttpExchange exchange) {
        String requestMethod = exchange.getRequestMethod();
        String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
        String logId = CommonUtil.generateLogIdByNanoTime(clientIP);
        logIdThreadLocal.set(logId);
        logger.info("[{}] - Received {} request with endpoint: {}", logId, requestMethod, exchange.getRequestURI());
        try {
            if (requestMethod.equalsIgnoreCase(RequestMethod.GET.name())) {
                this.handleGet(exchange);
            } else if (requestMethod.equalsIgnoreCase(RequestMethod.POST.name())) {
                this.handlePost(exchange);
            } else if (requestMethod.equalsIgnoreCase(RequestMethod.PUT.name())) {
                this.handlePut(exchange);
            } else if (requestMethod.equalsIgnoreCase(RequestMethod.DELETE.name())) {
                this.handleDelete(exchange);
            } else {
                sendResponse(exchange, "Method not supported", 405,
                        HttpStatus.INTERNAL_SERVER_ERROR.getMessageStatus());
            }
        } catch (IOException e) {
            logger.error("[{}] - Handle request fail ", logId, e);
        }
    }

    private void handleGet(HttpExchange httpExchange) throws IOException {
        Map<String, List<String>> params = CommonUtil.splitQuery(httpExchange.getRequestURI().getRawQuery());
        String response = "GET request received. Params: " + params;
        this.sendResponse(httpExchange, response, HttpStatus.SUCCESS.getCode(),
                HttpStatus.SUCCESS.getMessageStatus());
    }

    private void handlePost(HttpExchange httpExchange) {
        long start = System.currentTimeMillis();
        String logId = logIdThreadLocal.get();
        logger.info("[{}] - Start handle request POST in RequestHandler", logId);
        try {
            this.logClientIP(httpExchange);
            FeeCommandRequest feeCommandRequest = this.getPaymentRequestBody(httpExchange);
            boolean isExpired = this.checkIfExpiredRequest(feeCommandRequest, httpExchange);
//            boolean isExistRequestId = this.checkIfExistsRequestId(feeCommandRequest.getRequestId(), httpExchange);

            if (!isExpired ) {
                logger.info("[{}] - Start process fee command ", logId);
                this.processInitFee(httpExchange, feeCommandRequest);
            }
            long end = System.currentTimeMillis();
            logger.info("[{}] - Process request in RequestHandler take {} millisecond ", logId, (end - start));
        } catch (Exception e) {
            logger.error("[{}] - Error processing payment request", logId, e);
            this.sendResponse(httpExchange, HttpStatus.INTERNAL_SERVER_ERROR.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.getCode(), HttpStatus.INTERNAL_SERVER_ERROR.getMessageStatus());
        } finally {
            httpExchange.close();
        }
    }

    private void logClientIP(HttpExchange httpExchange) {
        String logId = logIdThreadLocal.get();
        try {
            String clientIP = httpExchange.getRemoteAddress().getAddress().getHostAddress();
            logger.info("[{}] - Handle request with clientIp {} ", logId, clientIP);
        } catch (Exception e) {
            logger.error("[{}] - Error getting client IP", logId, e);
        }
    }

    private FeeCommandRequest getPaymentRequestBody(HttpExchange httpExchange) throws IOException {
        String logId = logIdThreadLocal.get();
        InputStream is = httpExchange.getRequestBody();
        byte[] bytes = CommonUtil.getBytesFromInputStream(is);
        FeeCommandRequest feeCommandRequest = CommonUtil.bytesToObject(bytes, FeeCommandRequest.class);
        String responseLog = CommonUtil.objectToJson(feeCommandRequest);
        logger.info("[{}] - Handle request with requestBody: {} ", logId, responseLog);
        return feeCommandRequest;
    }

    private void processInitFee(HttpExchange httpExchange, FeeCommandRequest feeCommandRequest) {
        String logId = logIdThreadLocal.get();
        CommonUtil.pushRedis(feeCommandRequest.getRequestId());
        String endpoint = httpExchange.getRequestURI().getPath();
        logger.info("[{}] - Process request with endpoint: {} ", logId, endpoint);
        TransactionService transactionService = new TransactionServiceImpl();
        this.initFee(transactionService, feeCommandRequest, httpExchange);
    }

    private void initFee(TransactionService transactionService, FeeCommandRequest feeCommandRequest,
                         HttpExchange httpExchange) {
        String logId = logIdThreadLocal.get();
        FeeCommand feeCommand = new FeeCommand();
        feeCommand.setTotalFee(feeCommandRequest.getTotalFee());
        feeCommand.setTotalRecord(feeCommandRequest.getTotalRecord());
        transactionService.initFee(feeCommand);
        logger.info("[{}] - Init transaction successfully", logId);
        this.sendResponse(httpExchange, HttpStatus.SUCCESS.getMessage(),
                HttpStatus.SUCCESS.getCode(), HttpStatus.SUCCESS.getMessageStatus());
    }

    private void handlePut(HttpExchange httpExchange) throws IOException {
        long start = System.currentTimeMillis();
        String logId = logIdThreadLocal.get();
        logger.info("[{}] - Start handle request PUT in RequestHandler", logId);
        try {
            this.logClientIP(httpExchange);
            FeeCommandRequest feeCommandRequest = this.getPaymentRequestBody(httpExchange);

            boolean isExpired = this.checkIfExpiredRequest(feeCommandRequest, httpExchange);
            boolean isExistRequestId = this.checkIfExistsRequestId(feeCommandRequest.getRequestId(), httpExchange);

            if (!isExpired && !isExistRequestId) {
                this.processUpdateFee(httpExchange, feeCommandRequest);
                this.sendResponse(httpExchange, HttpStatus.SUCCESS.getMessage(),
                        HttpStatus.SUCCESS.getCode(), HttpStatus.SUCCESS.getMessageStatus());
            }
            long end = System.currentTimeMillis();
            logger.info("[{}] - Process PUT request in RequestHandler take {} millisecond ", logId, (end - start));
        } catch (Exception e) {
            logger.error("[{}] - Error processing update payment request", logId, e);
            this.sendResponse(httpExchange, HttpStatus.INTERNAL_SERVER_ERROR.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.getCode(), HttpStatus.INTERNAL_SERVER_ERROR.getMessageStatus());
        } finally {
            httpExchange.close();
        }
    }

    private void processUpdateFee(HttpExchange httpExchange, FeeCommandRequest feeCommandRequest) {
        String logId = logIdThreadLocal.get();
        logger.info("[{}] - Start process update fee transaction ", logId);
        CommonUtil.pushRedis(feeCommandRequest.getRequestId());
        String endpoint = httpExchange.getRequestURI().getPath();
        logger.info("[{}] - Process request update fee with endpoint: {} ", logId, endpoint);
        TransactionService transactionService = new TransactionServiceImpl();
        transactionService.updateFee(feeCommandRequest.getCommandCode());
    }
    private void handleDelete(HttpExchange httpExchange) {
        String response = "DELETE request received";
        this.sendResponse(httpExchange, response, HttpStatus.SUCCESS.getCode(), HttpStatus.SUCCESS.getMessageStatus());
    }

    private void sendResponse(HttpExchange httpExchange, String response, int statusCode, String statusMessage) {
        String logId = logIdThreadLocal.get();
        try {
            GeneralResponse<String> generalResponse = new GeneralResponse<>();
            httpExchange.getResponseHeaders().set("Content-Type", "application/json");
            generalResponse.setMessage(response);
            generalResponse.setCode(statusMessage);
            String responseString = CommonUtil.objectToJson(generalResponse);
            httpExchange.sendResponseHeaders(statusCode, responseString.length());
            OutputStream os = httpExchange.getResponseBody();
            os.write(responseString.getBytes());
            os.close();
            logger.info("[{}] - Send response to client successfully with response: {} ", logId, responseString);
        } catch (IOException e) {
            logger.error("[{}] - Occur error when write response", logId, e);
        }
    }

    private boolean checkIfExistsRequestId(String requestId, HttpExchange httpExchange) {
        String logId = logIdThreadLocal.get();
        boolean isExistsRequestId;
        isExistsRequestId = CommonUtil.isExistKey(requestId);
        if (isExistsRequestId) {
            logger.info("[{}] -Request with requestId: {} is existed", logId, requestId);
            this.sendResponse(httpExchange, HttpStatus.EXISTED_REQUEST_ID.getMessage(),
                    HttpStatus.EXISTED_REQUEST_ID.getCode(), HttpStatus.EXISTED_REQUEST_ID.getMessageStatus());
        }
        return isExistsRequestId;
    }

    private boolean checkIfExpiredRequest(FeeCommandRequest feeCommandRequest, HttpExchange httpExchange) {
        String logId = logIdThreadLocal.get();
        boolean isExpired;
        isExpired = CommonUtil.isExpired(feeCommandRequest.getRequestTime());
        if (isExpired) {
            logger.info("[{}] - Request with requestId: {} is expired", logId, feeCommandRequest.getRequestId());
            this.sendResponse(httpExchange, HttpStatus.EXPIRED_REQUEST.getMessage(),
                    HttpStatus.EXPIRED_REQUEST.getCode(), HttpStatus.EXPIRED_REQUEST.getMessageStatus());
        }
        return isExpired;

    }

}