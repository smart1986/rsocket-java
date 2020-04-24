package io.rsocket.frame;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public class RequestNFrameFlyweight {
  private RequestNFrameFlyweight() {}

  public static ByteBuf encode(
      final ByteBufAllocator allocator, final int streamId, long requestN) {

    if (requestN < 1) {
      throw new IllegalArgumentException("request n is less than 1");
    }

    int reqN = requestN > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) requestN;

    ByteBuf header = FrameHeaderFlyweight.encode(allocator, streamId, FrameType.REQUEST_N, 0);
    return header.writeInt(reqN);
  }

  public static long requestN(ByteBuf byteBuf) {
    FrameHeaderFlyweight.ensureFrameType(FrameType.REQUEST_N, byteBuf);
    byteBuf.markReaderIndex();
    byteBuf.skipBytes(FrameHeaderFlyweight.size());
    int i = byteBuf.readInt();
    byteBuf.resetReaderIndex();
    return i == Integer.MAX_VALUE ? Long.MAX_VALUE : i;
  }
}
