/*
 * Copyright 2015-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.rsocket.core;

import static io.rsocket.frame.FrameLengthCodec.FRAME_LENGTH_MASK;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.CharsetUtil;
import io.rsocket.Payload;
import io.rsocket.buffer.LeaksTrackingByteBufAllocator;
import io.rsocket.frame.FrameType;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.util.ByteBufPayload;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import org.assertj.core.api.Assertions;
import reactor.core.Exceptions;
import reactor.util.annotation.Nullable;

final class TestRequesterResponderSupport extends RequesterResponderSupport {

  static final String DATA_CONTENT = "testData";
  static final String METADATA_CONTENT = "testMetadata";

  final Throwable error;

  TestRequesterResponderSupport(
      @Nullable Throwable error,
      StreamIdSupplier streamIdSupplier,
      int mtu,
      int maxFrameLength,
      int maxInboundPayloadSize) {
    super(
        mtu,
        maxFrameLength,
        maxInboundPayloadSize,
        PayloadDecoder.ZERO_COPY,
        LeaksTrackingByteBufAllocator.instrument(ByteBufAllocator.DEFAULT),
        streamIdSupplier);
    this.error = error;
  }

  static Payload genericPayload(LeaksTrackingByteBufAllocator allocator) {
    ByteBuf data = allocator.buffer();
    data.writeCharSequence(DATA_CONTENT, CharsetUtil.UTF_8);

    ByteBuf metadata = allocator.buffer();
    metadata.writeCharSequence(METADATA_CONTENT, CharsetUtil.UTF_8);

    return ByteBufPayload.create(data, metadata);
  }

  static Payload randomPayload(LeaksTrackingByteBufAllocator allocator) {
    boolean hasMetadata = ThreadLocalRandom.current().nextBoolean();
    ByteBuf metadataByteBuf;
    if (hasMetadata) {
      byte[] randomMetadata = new byte[ThreadLocalRandom.current().nextInt(0, 512)];
      ThreadLocalRandom.current().nextBytes(randomMetadata);
      metadataByteBuf = allocator.buffer().writeBytes(randomMetadata);
    } else {
      metadataByteBuf = null;
    }
    byte[] randomData = new byte[ThreadLocalRandom.current().nextInt(512, 1024)];
    ThreadLocalRandom.current().nextBytes(randomData);

    ByteBuf dataByteBuf = allocator.buffer().writeBytes(randomData);
    return ByteBufPayload.create(dataByteBuf, metadataByteBuf);
  }

  static ArrayList<ByteBuf> prepareFragments(
      LeaksTrackingByteBufAllocator allocator, int mtu, Payload payload) {
    boolean hasMetadata = payload.hasMetadata();
    ByteBuf data = payload.sliceData();
    ByteBuf metadata = payload.sliceMetadata();
    ArrayList<ByteBuf> fragments = new ArrayList<>();

    fragments.add(
        FragmentationUtils.encodeFirstFragment(
            allocator, mtu, FrameType.NEXT_COMPLETE, 1, hasMetadata, metadata, data));

    while (metadata.isReadable() || data.isReadable()) {
      fragments.add(
          FragmentationUtils.encodeFollowsFragment(allocator, mtu, 1, true, metadata, data));
    }

    return fragments;
  }

  @Override
  public synchronized int getNextStreamId() {
    int nextStreamId = super.getNextStreamId();

    if (error != null) {
      throw Exceptions.propagate(error);
    }

    return nextStreamId;
  }

  @Override
  public synchronized int addAndGetNextStreamId(FrameHandler frameHandler) {
    int nextStreamId = super.addAndGetNextStreamId(frameHandler);

    if (error != null) {
      super.remove(nextStreamId, frameHandler);
      throw Exceptions.propagate(error);
    }

    return nextStreamId;
  }

  public static TestRequesterResponderSupport client(@Nullable Throwable e) {
    return client(0, FRAME_LENGTH_MASK, Integer.MAX_VALUE, e);
  }

  public static TestRequesterResponderSupport client(
      int mtu, int maxFrameLength, int maxInboundPayloadSize, @Nullable Throwable e) {
    return new TestRequesterResponderSupport(
        e, StreamIdSupplier.clientSupplier(), mtu, maxFrameLength, maxInboundPayloadSize);
  }

  public static TestRequesterResponderSupport client(
      int mtu, int maxFrameLength, int maxInboundPayloadSize) {
    return client(mtu, maxFrameLength, maxInboundPayloadSize, null);
  }

  public static TestRequesterResponderSupport client(int mtu, int maxFrameLength) {
    return client(mtu, maxFrameLength, Integer.MAX_VALUE);
  }

  public static TestRequesterResponderSupport client(int mtu) {
    return client(mtu, FRAME_LENGTH_MASK);
  }

  public static TestRequesterResponderSupport client() {
    return client(0);
  }

  public TestRequesterResponderSupport assertNoActiveStreams() {
    Assertions.assertThat(activeStreams).isEmpty();
    return this;
  }

  public TestRequesterResponderSupport assertHasStream(int i, FrameHandler stream) {
    Assertions.assertThat(activeStreams).containsEntry(i, stream);
    return this;
  }

  @Override
  public LeaksTrackingByteBufAllocator getAllocator() {
    return (LeaksTrackingByteBufAllocator) super.getAllocator();
  }
}