package org.gsc.net.message;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import common.utils.Sha256Hash;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Message {

  protected static final Logger logger = LoggerFactory.getLogger("Net");

  protected boolean unpacked;
  protected byte[] data;
  protected byte type;

  public Message() {
  }

  public Message(byte[] packed) {
    this.data = packed;
    unpacked = false;
  }

  public Message(byte type, byte[] packed) {
    this.type = type;
    this.data = packed;
    unpacked = false;
  }

  public ByteBuf getSendData() {
    return wrappedBuffer(ArrayUtils.add(this.getData(), 0, type));
  }

  public Sha256Hash getMessageId() {
    return Sha256Hash.of(getData());
  }

  public abstract byte[] getData();

  public String toString() {
    return "[Message Type: " + getType() + ", Message Hash: " + getMessageId() + "]";
  }

  public abstract Class<?> getAnswerMessage();

  //public byte getCode() { return type; }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Message)) {
      return false;
    }
    Message message = (Message) o;
    return Arrays.equals(data, message.data);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(data);
  }

  public abstract MessageTypes getType();

}
