package org.gsc.db;

import java.util.Arrays;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.gsc.common.exception.BadItemException;
import org.gsc.common.exception.BalanceInsufficientException;
import org.gsc.common.exception.ContractExeException;
import org.gsc.common.exception.ContractValidateException;
import org.gsc.common.exception.DupTransactionException;
import org.gsc.common.exception.ItemNotFoundException;
import org.gsc.common.exception.RevokingStoreIllegalStateException;
import org.gsc.common.exception.TaposException;
import org.gsc.common.exception.ValidateSignatureException;
import org.gsc.common.utils.ByteArray;
import org.gsc.core.Constant;
import org.gsc.core.wrapper.AccountWrapper;
import org.gsc.core.wrapper.BlockWrapper;
import org.gsc.core.wrapper.TransactionWrapper;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;

@Getter
@Slf4j
public class Manager {

  @Autowired
  private AccountStore accountStore;

  @Autowired
  private TransactionStore transactionStore;

  @Autowired
  private BlockStore blockStore;

  @Autowired
  private ProducerStore prodStore;

  @Autowired
  private AssetIssueStore assetIssueStore;

  @Autowired
  private GlobalPropertiesStore globalPropertiesStore;

  @Autowired
  private BlockIndexStore blockIndexStore;
//  @Autowired
//  private AccountIndexStore accountIndexStore;
  @Autowired
  private ProducerScheduleStore prodScheduleStore;

  @Autowired
  private TaposBlockStore taposStore;

  @Autowired
  private VotesStore votesStore;
//
  @Autowired
  private PeersStore peersStore;

  @Autowired
  private ForkDatabase forkDB;

  @Getter
  private BlockWrapper genesisBlock;

  @Autowired
  private UndoStore undoStore;


  /**
   * judge balance.
   */
  public void adjustBalance(byte[] accountAddress, long amount)
      throws BalanceInsufficientException {
    AccountWrapper account = getAccountStore().get(accountAddress);
    long balance = account.getBalance();
    if (amount == 0) {
      return;
    }

    if (amount < 0 && balance < -amount) {
      throw new BalanceInsufficientException(accountAddress + " Insufficient");
    }
    account.setBalance(Math.addExact(balance, amount));
    this.getAccountStore().put(account.getAddress().toByteArray(), account);
  }

  public long getHeadBlockTimeStamp() {
    return 0L;
    //TODO
    //return getDynamicPropertiesStore().getLatestBlockHeaderTimestamp();
  }

  public boolean lastHeadBlockIsMaintenance() {
    return getGlobalPropertiesStore().getStateFlag() == 1;
  }

  public void adjustAllowance(byte[] accountAddress, long amount)
      throws BalanceInsufficientException {
    AccountWrapper account = getAccountStore().get(accountAddress);
    long allowance = account.getAllowance();
    if (amount == 0) {
      return;
    }

    if (amount < 0 && allowance < -amount) {
      throw new BalanceInsufficientException(accountAddress + " Insufficient");
    }
    account.setAllowance(allowance + amount);
    this.getAccountStore().put(account.createDbKey(), account);
  }

  void validateTapos(TransactionWrapper transactionCapsule) throws TaposException {
    byte[] refBlockHash = transactionCapsule.getInstance()
        .getRawData().getRefBlockHash().toByteArray();
    byte[] refBlockNumBytes = transactionCapsule.getInstance()
        .getRawData().getRefBlockBytes().toByteArray();
    try {
      byte[] blockHash = this.taposStore.get(refBlockNumBytes).getData();
      if (Arrays.equals(blockHash, refBlockHash)) {
        return;
      } else {
        String str = String.format(
            "Tapos failed, different block hash, %s, %s , recent block %s, solid block %s head block %s",
            ByteArray.toLong(refBlockNumBytes), Hex.toHexString(refBlockHash),
            Hex.toHexString(blockHash),
            getSolidBlockId().getString(), getHeadBlockId().getString()).toString();
        logger.info(str);
        throw new TaposException(str);

      }
    } catch (ItemNotFoundException e) {
      String str = String.
          format("Tapos failed, block not found, ref block %s, %s , solid block %s head block %s",
              ByteArray.toLong(refBlockNumBytes), Hex.toHexString(refBlockHash),
              getSolidBlockId().getString(), getHeadBlockId().getString()).toString();
      logger.info(str);
      throw new TaposException(str);
    }
  }

  void validateCommon(TransactionWrapper transactionCapsule)
      throws TransactionExpirationException, TooBigTransactionException {
    if (transactionCapsule.getData().length > Constant.TRANSACTION_MAX_BYTE_SIZE) {
      throw new TooBigTransactionException(
          "too big transaction, the size is " + transactionCapsule.getData().length + " bytes");
    }
    long transactionExpiration = transactionCapsule.getExpiration();
    long headBlockTime = getHeadBlockTimeStamp();
    if (transactionExpiration <= headBlockTime ||
        transactionExpiration > headBlockTime + Constant.MAXIMUM_TIME_UNTIL_EXPIRATION) {
      throw new TransactionExpirationException(
          "transaction expiration, transaction expiration time is " + transactionExpiration
              + ", but headBlockTime is " + headBlockTime);
    }
  }

  void validateDup(TransactionWrapper transactionCapsule) throws DupTransactionException {
    if (getTransactionStore().get(transactionCapsule.getTransactionId().getBytes()) != null) {
      logger.debug(
          getTransactionStore().get(transactionCapsule.getTransactionId().getBytes()).toString());
      throw new DupTransactionException("dup trans");
    }
  }

  /**
   * push transaction into db.
   */
  public boolean pushTransactions(final TransactionWrapper trx)
      throws ValidateSignatureException, ContractValidateException, ContractExeException,
      ValidateBandwidthException, DupTransactionException, TaposException,
      TooBigTransactionException, TransactionExpirationException {
    logger.info("push transaction");

    if (!trx.validateSignature()) {
      throw new ValidateSignatureException("trans sig validate failed");
    }

    //validateFreq(trx);
    synchronized (this) {
      if (!dialog.valid()) {
        dialog.setValue(undoStore.buildDialog());
      }

      try (dialog.Dialog tmpDialog = dialog.buildDialog()) {
        processTransaction(trx);
        pendingTransactions.add(trx);
        tmpDialog.merge();
      } catch (RevokingStoreIllegalStateException e) {
        logger.debug(e.getMessage(), e);
      }
    }
    return true;
  }

  public void consumeBandwidth(TransactionWrapper trx) throws ValidateBandwidthException {
    BandwidthProcessor processor = new BandwidthProcessor(this);
    processor.consumeBandwidth(trx);
  }
  /**
   * when switch fork need erase blocks on fork branch.
   */
  public void eraseBlock() throws BadItemException, ItemNotFoundException {
    dialog.reset();
    BlockWrapper oldHeadBlock =
        getBlockStore().get(globalPropertiesStore.getLatestBlockHeaderHash().getBytes());
    try {
      revokingStore.pop();
    } catch (RevokingStoreIllegalStateException e) {
      logger.info(e.getMessage(), e);
    }
    logger.info("erase block:" + oldHeadBlock);
    forkDB.pop();
    popedTransactions.addAll(oldHeadBlock.getTransactions());
  }

  private void applyBlock(BlockWrapper block)
      throws ContractValidateException, ContractExeException, ValidateSignatureException, ValidateBandwidthException, TransactionExpirationException, TooBigTransactionException, DupTransactionException, TaposException {
    processBlock(block);
    this.blockStore.put(block.getBlockId().getBytes(), block);
    this.blockIndexStore.put(block.getBlockId());
  }


}
