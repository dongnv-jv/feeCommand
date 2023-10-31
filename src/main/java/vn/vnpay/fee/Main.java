package vn.vnpay.fee;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.vnpay.fee.config.database.DataSourceConfig;
import vn.vnpay.fee.config.redis.RedisConfig;
import vn.vnpay.fee.controller.FeeController;
import vn.vnpay.fee.job.ScheduleScanTime;
import vn.vnpay.fee.service.impl.TransactionServiceImpl;

import java.util.Arrays;
import java.util.List;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            DataSourceConfig.initDatabaseConnectionPool();
            RedisConfig.initRedisConfig();
            TransactionServiceImpl transactionService = new TransactionServiceImpl();
            ScheduleScanTime scheduleScanTime = new ScheduleScanTime();
            scheduleScanTime.cronJob(transactionService);
            FeeController feeController = new FeeController();
            List<String> listPath = Arrays.asList("/init", "/update");
            feeController.start(listPath);
//            transactionService.updateStatus("bdf055ba-74a0-4d2e-81f7-6c20a3c0b2c9");
        } catch (Exception e) {
            logger.error("Failed to initialize", e);
            System.exit(3);
        }
    }
}