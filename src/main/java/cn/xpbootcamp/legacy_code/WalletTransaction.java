package cn.xpbootcamp.legacy_code;

import cn.xpbootcamp.legacy_code.enums.STATUS;
import cn.xpbootcamp.legacy_code.service.WalletService;
import cn.xpbootcamp.legacy_code.service.WalletServiceImpl;
import cn.xpbootcamp.legacy_code.utils.IdGenerator;
import cn.xpbootcamp.legacy_code.utils.RedisDistributedLock;

import javax.transaction.InvalidTransactionException;

public class WalletTransaction {
    private String id;
    private Long buyerId;
    private Long sellerId;
    private Long createdTimestamp;
    private Double amount;
    private STATUS status;
    private String walletTransactionId;


    public WalletTransaction(String preAssignedId, Long buyerId, Long sellerId) {
        if (preAssignedId != null && !preAssignedId.isEmpty()) {
            this.id = preAssignedId;
        } else {
            this.id = IdGenerator.generateTransactionId();
        }
        if (!this.id.startsWith("t_")) {
            this.id = "t_" + preAssignedId;
        }
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.status = STATUS.TO_BE_EXECUTED;
        this.createdTimestamp = System.currentTimeMillis();
    }

    public boolean transferMoney() throws InvalidTransactionException {
        judgeInvalidTransaction();
        if (statusIsExecuted()) return true;
        boolean isLocked = false;
        try {
            isLocked = RedisDistributedLock.getSingletonInstance().lock(id);

            if (!isLocked) {
                return false;
            }
            if (statusIsExecuted()) return true; // double check
            if (changeStatusToExpired()) return false;
            String walletTransactionId = transferToSeller();
            return changeStatusByTransferResult(walletTransactionId);
        } finally {
            if (isLocked) {
                RedisDistributedLock.getSingletonInstance().unlock(id);
            }
        }
    }

    private boolean statusIsExecuted() {
        return status == STATUS.EXECUTED;
    }

    private boolean changeStatusByTransferResult(String walletTransactionId) {
        if (walletTransactionId != null) {
            this.walletTransactionId = walletTransactionId;
            this.status = STATUS.EXECUTED;
            return true;
        } else {
            this.status = STATUS.FAILED;
            return false;
        }
    }

    private String transferToSeller() {
        WalletService walletService = new WalletServiceImpl();
        return walletService.moveMoney(id, buyerId, sellerId, amount);
    }

    private boolean changeStatusToExpired() {
        long executionInvokedTimestamp = System.currentTimeMillis();
        int twentyDays = 1728000000;
        if (executionInvokedTimestamp - createdTimestamp > twentyDays) {
            this.status = STATUS.EXPIRED;
            return true;
        }
        return false;
    }

    private void judgeInvalidTransaction() throws InvalidTransactionException {
        if (buyerId == null || (sellerId == null || amount < 0.0)) {
            throw new InvalidTransactionException("This is an invalid transaction");
        }
    }

}
