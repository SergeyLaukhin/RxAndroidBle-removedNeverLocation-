package com.polidea.rxandroidble2.mockrxandroidble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.Nullable;

import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.RxBleDeviceServices;
import com.polidea.rxandroidble2.Timeout;
import com.polidea.rxandroidble2.exceptions.BleAlreadyConnectedException;
import com.polidea.rxandroidble2.scan.ScanRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.subjects.BehaviorSubject;

import static com.polidea.rxandroidble2.RxBleConnection.RxBleConnectionState.CONNECTED;
import static com.polidea.rxandroidble2.RxBleConnection.RxBleConnectionState.CONNECTING;
import static com.polidea.rxandroidble2.RxBleConnection.RxBleConnectionState.DISCONNECTED;

public class RxBleDeviceMock implements RxBleDevice {

    private RxBleConnection rxBleConnection;
    private BehaviorSubject<RxBleConnection.RxBleConnectionState> connectionStateBehaviorSubject = BehaviorSubject.createDefault(
            DISCONNECTED
    );
    private String name;
    private String macAddress;
    private Integer rssi;
    private byte[] legacyScanRecord;
    private ScanRecord scanRecord;
    private List<UUID> advertisedUUIDs;
    private BluetoothDevice bluetoothDevice;
    private AtomicBoolean isConnected = new AtomicBoolean(false);

    public RxBleDeviceMock(String name,
                           String macAddress,
                           byte[] scanRecord,
                           Integer rssi,
                           RxBleDeviceServices rxBleDeviceServices,
                           Map<UUID, Observable<byte[]>> characteristicNotificationSources,
                           @Nullable BluetoothDevice bluetoothDevice) {
        this.name = name;
        this.macAddress = macAddress;
        this.rxBleConnection = new RxBleConnectionMock(rxBleDeviceServices,
                rssi,
                characteristicNotificationSources,
                new HashMap<UUID, Function<BluetoothGattCharacteristic, Single<byte[]>>>(),
                new HashMap<UUID, BiFunction<BluetoothGattCharacteristic, byte[], Completable>>(),
                new HashMap<UUID, Map<UUID, Function<BluetoothGattDescriptor, Single<byte[]>>>>(),
                new HashMap<UUID, Map<UUID, BiFunction<BluetoothGattDescriptor, byte[], Completable>>>());
        this.rssi = rssi;
        this.legacyScanRecord = scanRecord;
        this.advertisedUUIDs = new ArrayList<>();
        this.bluetoothDevice = bluetoothDevice;
    }

    public RxBleDeviceMock(String name,
                           String macAddress,
                           ScanRecord scanRecord,
                           Integer rssi,
                           @Nullable BluetoothDevice bluetoothDevice,
                           RxBleConnectionMock connectionMock
    ) {
        this.name = name;
        this.macAddress = macAddress;
        this.rxBleConnection = connectionMock;
        this.rssi = rssi;
        this.scanRecord = scanRecord;
        this.advertisedUUIDs = new ArrayList<>();
        this.bluetoothDevice = bluetoothDevice;
    }

    public RxBleDeviceMock(String name,
                           String macAddress,
                           ScanRecord scanRecord,
                           Integer rssi,
                           RxBleDeviceServices rxBleDeviceServices,
                           Map<UUID, Observable<byte[]>> characteristicNotificationSources,
                           Map<UUID, Function<BluetoothGattCharacteristic, Single<byte[]>>> characteristicReadCallbacks,
                           Map<UUID, BiFunction<BluetoothGattCharacteristic, byte[], Completable>> characteristicWriteCallbacks,
                           Map<UUID, Map<UUID, Function<BluetoothGattDescriptor, Single<byte[]>>>> descriptorReadCallbacks,
                           Map<UUID, Map<UUID, BiFunction<BluetoothGattDescriptor, byte[], Completable>>> descriptorWriteCallbacks,
                           @Nullable BluetoothDevice bluetoothDevice) {
        this(
                name,
                macAddress,
                scanRecord,
                rssi,
                bluetoothDevice,
                new RxBleConnectionMock(
                    rxBleDeviceServices,
                    rssi,
                    characteristicNotificationSources,
                    characteristicReadCallbacks,
                    characteristicWriteCallbacks,
                    descriptorReadCallbacks,
                    descriptorWriteCallbacks
                )
        );
    }

    public void addAdvertisedUUID(UUID advertisedUUID) {
        advertisedUUIDs.add(advertisedUUID);
    }

    @Override
    public Observable<RxBleConnection> establishConnection(boolean autoConnect) {
        return Observable.defer(new Callable<Observable<RxBleConnection>>() {
            @Override
            public Observable<RxBleConnection> call() {
                if (isConnected.compareAndSet(false, true)) {
                    return RxBleDeviceMock.this.emitConnectionWithoutCompleting()
                            .doOnSubscribe(new Consumer<Disposable>() {
                                @Override
                                public void accept(Disposable disposable) throws Exception {
                                    connectionStateBehaviorSubject.onNext(CONNECTING);
                                }
                            })
                            .doOnNext(new Consumer<RxBleConnection>() {
                                @Override
                                public void accept(RxBleConnection rxBleConnection) throws Exception {
                                    connectionStateBehaviorSubject.onNext(CONNECTED);
                                }
                            })
                            .doFinally(new Action() {
                                @Override
                                public void run() {
                                    connectionStateBehaviorSubject.onNext(DISCONNECTED);
                                    isConnected.set(false);
                                }
                            });
                } else {
                    return Observable.error(new BleAlreadyConnectedException(macAddress));
                }
            }
        });
    }

    @Override
    public Observable<RxBleConnection> establishConnection(boolean autoConnect, Timeout operationTimeout) {
        return establishConnection(autoConnect);
    }

    private Observable<RxBleConnection> emitConnectionWithoutCompleting() {
        return Observable.<RxBleConnection>never().startWith(rxBleConnection);
    }

    public List<UUID> getAdvertisedUUIDs() {
        return advertisedUUIDs;
    }

    @Override
    public RxBleConnection.RxBleConnectionState getConnectionState() {
        return observeConnectionStateChanges().blockingFirst();
    }

    @Override
    public String getMacAddress() {
        return macAddress;
    }

    @Override
    public BluetoothDevice getBluetoothDevice() {
        if (bluetoothDevice != null) {
            return bluetoothDevice;
        }
        throw new IllegalStateException("Mock is not configured to return a BluetoothDevice");
    }

    @Override
    public String getName() {
        return name;
    }

    public Integer getRssi() {
        return rssi;
    }

    public byte[] getLegacyScanRecord() {
        return legacyScanRecord;
    }

    public ScanRecord getScanRecord() {
        return scanRecord;
    }

    @Override
    public Observable<RxBleConnection.RxBleConnectionState> observeConnectionStateChanges() {
        return connectionStateBehaviorSubject.distinctUntilChanged();
    }

    @Override
    public String toString() {
        return "RxBleDeviceImpl{" + "bluetoothDevice=" + name + '(' + macAddress + ')' + '}';
    }
}
