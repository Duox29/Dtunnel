package com.duox.dtunnel.grpc.pb;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.65.0)",
    comments = "Source: duox/agent/v1/agent.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class AgentServiceGrpc {

  private AgentServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "duox.agent.v1.AgentService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.duox.dtunnel.grpc.pb.RegisterRequest,
      com.duox.dtunnel.grpc.pb.RegisterResponse> getRegisterMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Register",
      requestType = com.duox.dtunnel.grpc.pb.RegisterRequest.class,
      responseType = com.duox.dtunnel.grpc.pb.RegisterResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.duox.dtunnel.grpc.pb.RegisterRequest,
      com.duox.dtunnel.grpc.pb.RegisterResponse> getRegisterMethod() {
    io.grpc.MethodDescriptor<com.duox.dtunnel.grpc.pb.RegisterRequest, com.duox.dtunnel.grpc.pb.RegisterResponse> getRegisterMethod;
    if ((getRegisterMethod = AgentServiceGrpc.getRegisterMethod) == null) {
      synchronized (AgentServiceGrpc.class) {
        if ((getRegisterMethod = AgentServiceGrpc.getRegisterMethod) == null) {
          AgentServiceGrpc.getRegisterMethod = getRegisterMethod =
              io.grpc.MethodDescriptor.<com.duox.dtunnel.grpc.pb.RegisterRequest, com.duox.dtunnel.grpc.pb.RegisterResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Register"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.duox.dtunnel.grpc.pb.RegisterRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.duox.dtunnel.grpc.pb.RegisterResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AgentServiceMethodDescriptorSupplier("Register"))
              .build();
        }
      }
    }
    return getRegisterMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.duox.dtunnel.grpc.pb.AgentMessage,
      com.duox.dtunnel.grpc.pb.ServerMessage> getControlMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Control",
      requestType = com.duox.dtunnel.grpc.pb.AgentMessage.class,
      responseType = com.duox.dtunnel.grpc.pb.ServerMessage.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.duox.dtunnel.grpc.pb.AgentMessage,
      com.duox.dtunnel.grpc.pb.ServerMessage> getControlMethod() {
    io.grpc.MethodDescriptor<com.duox.dtunnel.grpc.pb.AgentMessage, com.duox.dtunnel.grpc.pb.ServerMessage> getControlMethod;
    if ((getControlMethod = AgentServiceGrpc.getControlMethod) == null) {
      synchronized (AgentServiceGrpc.class) {
        if ((getControlMethod = AgentServiceGrpc.getControlMethod) == null) {
          AgentServiceGrpc.getControlMethod = getControlMethod =
              io.grpc.MethodDescriptor.<com.duox.dtunnel.grpc.pb.AgentMessage, com.duox.dtunnel.grpc.pb.ServerMessage>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Control"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.duox.dtunnel.grpc.pb.AgentMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.duox.dtunnel.grpc.pb.ServerMessage.getDefaultInstance()))
              .setSchemaDescriptor(new AgentServiceMethodDescriptorSupplier("Control"))
              .build();
        }
      }
    }
    return getControlMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static AgentServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AgentServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AgentServiceStub>() {
        @java.lang.Override
        public AgentServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AgentServiceStub(channel, callOptions);
        }
      };
    return AgentServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static AgentServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AgentServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AgentServiceBlockingStub>() {
        @java.lang.Override
        public AgentServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AgentServiceBlockingStub(channel, callOptions);
        }
      };
    return AgentServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static AgentServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AgentServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AgentServiceFutureStub>() {
        @java.lang.Override
        public AgentServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AgentServiceFutureStub(channel, callOptions);
        }
      };
    return AgentServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * First-run device registration (detail.md §6). Unary, mirrors the REST
     * /agent/v1/register contract.
     * </pre>
     */
    default void register(com.duox.dtunnel.grpc.pb.RegisterRequest request,
        io.grpc.stub.StreamObserver<com.duox.dtunnel.grpc.pb.RegisterResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRegisterMethod(), responseObserver);
    }

    /**
     * <pre>
     * Steady-state control stream. The agent opens it once (authenticated by its
     * device token in the Hello message) and keeps it open:
     *   server -&gt; agent: ConfigPush on every desired-state version bump,
     *                    Revoked the instant an admin revokes the device,
     *                    HeartbeatAck for each heartbeat;
     *   agent  -&gt; server: Hello (handshake), then periodic Heartbeat.
     * This is what bounds revocation latency to milliseconds instead of the
     * heartbeat interval (detail.md §4).
     * </pre>
     */
    default io.grpc.stub.StreamObserver<com.duox.dtunnel.grpc.pb.AgentMessage> control(
        io.grpc.stub.StreamObserver<com.duox.dtunnel.grpc.pb.ServerMessage> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getControlMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service AgentService.
   */
  public static abstract class AgentServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return AgentServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service AgentService.
   */
  public static final class AgentServiceStub
      extends io.grpc.stub.AbstractAsyncStub<AgentServiceStub> {
    private AgentServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AgentServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AgentServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * First-run device registration (detail.md §6). Unary, mirrors the REST
     * /agent/v1/register contract.
     * </pre>
     */
    public void register(com.duox.dtunnel.grpc.pb.RegisterRequest request,
        io.grpc.stub.StreamObserver<com.duox.dtunnel.grpc.pb.RegisterResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRegisterMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Steady-state control stream. The agent opens it once (authenticated by its
     * device token in the Hello message) and keeps it open:
     *   server -&gt; agent: ConfigPush on every desired-state version bump,
     *                    Revoked the instant an admin revokes the device,
     *                    HeartbeatAck for each heartbeat;
     *   agent  -&gt; server: Hello (handshake), then periodic Heartbeat.
     * This is what bounds revocation latency to milliseconds instead of the
     * heartbeat interval (detail.md §4).
     * </pre>
     */
    public io.grpc.stub.StreamObserver<com.duox.dtunnel.grpc.pb.AgentMessage> control(
        io.grpc.stub.StreamObserver<com.duox.dtunnel.grpc.pb.ServerMessage> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getControlMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service AgentService.
   */
  public static final class AgentServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<AgentServiceBlockingStub> {
    private AgentServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AgentServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AgentServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * First-run device registration (detail.md §6). Unary, mirrors the REST
     * /agent/v1/register contract.
     * </pre>
     */
    public com.duox.dtunnel.grpc.pb.RegisterResponse register(com.duox.dtunnel.grpc.pb.RegisterRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRegisterMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service AgentService.
   */
  public static final class AgentServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<AgentServiceFutureStub> {
    private AgentServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AgentServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AgentServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * First-run device registration (detail.md §6). Unary, mirrors the REST
     * /agent/v1/register contract.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.duox.dtunnel.grpc.pb.RegisterResponse> register(
        com.duox.dtunnel.grpc.pb.RegisterRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRegisterMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REGISTER = 0;
  private static final int METHODID_CONTROL = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_REGISTER:
          serviceImpl.register((com.duox.dtunnel.grpc.pb.RegisterRequest) request,
              (io.grpc.stub.StreamObserver<com.duox.dtunnel.grpc.pb.RegisterResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_CONTROL:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.control(
              (io.grpc.stub.StreamObserver<com.duox.dtunnel.grpc.pb.ServerMessage>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getRegisterMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.duox.dtunnel.grpc.pb.RegisterRequest,
              com.duox.dtunnel.grpc.pb.RegisterResponse>(
                service, METHODID_REGISTER)))
        .addMethod(
          getControlMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              com.duox.dtunnel.grpc.pb.AgentMessage,
              com.duox.dtunnel.grpc.pb.ServerMessage>(
                service, METHODID_CONTROL)))
        .build();
  }

  private static abstract class AgentServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    AgentServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.duox.dtunnel.grpc.pb.AgentProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("AgentService");
    }
  }

  private static final class AgentServiceFileDescriptorSupplier
      extends AgentServiceBaseDescriptorSupplier {
    AgentServiceFileDescriptorSupplier() {}
  }

  private static final class AgentServiceMethodDescriptorSupplier
      extends AgentServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    AgentServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (AgentServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new AgentServiceFileDescriptorSupplier())
              .addMethod(getRegisterMethod())
              .addMethod(getControlMethod())
              .build();
        }
      }
    }
    return result;
  }
}
