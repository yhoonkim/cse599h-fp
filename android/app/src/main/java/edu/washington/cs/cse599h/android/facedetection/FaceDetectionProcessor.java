// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//            http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package edu.washington.cs.cse599h.android.facedetection;

import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Camera;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.washington.cs.cse599h.android.ble.BleManager;
import edu.washington.cs.cse599h.android.camera.CameraSource;
import edu.washington.cs.cse599h.android.camera.FrameMetadata;
import edu.washington.cs.cse599h.android.camera.GraphicOverlay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import edu.washington.cs.cse599h.android.ble.BleUtils;

/** Face Detector Demo. */
public class FaceDetectionProcessor extends VisionProcessorBase<List<FirebaseVisionFace>> {

    private static final String TAG = "FaceDetectionProcessor";
    private static final String DATA_PREFIX = "!F";

    private final FirebaseVisionFaceDetector detector;
    private final Context mContext;
    private final CameraSource mCameraSource;

    SparseArray<DescriptiveStatistics> normalizedY = new SparseArray<>();
    SparseArray<DescriptiveStatistics> normalizedX = new SparseArray<>();

    protected BleManager mBleManager;
    protected BluetoothGattService mUartService;

    private final int imgWidth = 1280;
    private final int imgHeight = 960;

    private final int SLIDING_WINDOW_SIZE = 10;

    public FaceDetectionProcessor(Context context, CameraSource cameraSource, BleManager bleManager, BluetoothGattService uartService) {
        FirebaseVisionFaceDetectorOptions options =
              new FirebaseVisionFaceDetectorOptions.Builder()
                    //.setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                    .setClassificationMode(FirebaseVisionFaceDetectorOptions.NO_CLASSIFICATIONS)
                    .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                    //.setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
                    .enableTracking()
                    .build();

        detector = FirebaseVision.getInstance().getVisionFaceDetector(options);
        mBleManager = bleManager;
        mUartService = uartService;
        mContext = context;
        mCameraSource = cameraSource;
    }

    @Override
    public void stop() {
        try {
            detector.close();
        } catch (IOException e) {
            Log.e(TAG, "Exception thrown while trying to close Face Detector: " + e);
        }
    }

    @Override
    protected Task<List<FirebaseVisionFace>> detectInImage(FirebaseVisionImage image) {
        return detector.detectInImage(image);
    }

    @Override
    protected void onSuccess(
            @NonNull List<FirebaseVisionFace> faces,
            @NonNull FrameMetadata frameMetadata,
            @NonNull GraphicOverlay graphicOverlay) {
        graphicOverlay.clear();

        //SparseArray<Double> isSpeakingList = new SparseArray<>();
        List<FirebaseVisionFace> speakerList = new ArrayList<>();

        for (int i = 0; i < faces.size(); ++i) {
            FirebaseVisionFace face = faces.get(i);
            FaceGraphic faceGraphic = new FaceGraphic(graphicOverlay);
            graphicOverlay.add(faceGraphic);
            storeLipMovements(face);
            double isSpeaking = isSpeaking(face);
            if (isSpeaking > 0) {
                speakerList.add(face);
            }
            //isSpeakingList.put(face.getTrackingId(), isSpeaking);
            faceGraphic.updateFace(face, frameMetadata.getCameraFacing(), isSpeaking(face), calculateAngle(face.getBoundingBox().centerX()));
        }

        if (faces.size() > 0) {
            if (speakerList.size() == 1) {
                Log.d(TAG, "angle single");
                sendDataWithCRC((float)(calculateAngle(speakerList.get(0).getBoundingBox().centerX())));
            } else if (speakerList.size() > 0){
                double avgX = 0.0;
                for (FirebaseVisionFace f: speakerList) {
                    avgX+=f.getBoundingBox().centerX();
                }
                avgX /= speakerList.size();

                Log.d(TAG, "angle avg");
                sendDataWithCRC((float)(calculateAngle((int)avgX)));
            } else if (speakerList.size() == 0) {
                double avgX = 0.0;
                for (FirebaseVisionFace f: faces) {
                    avgX+=f.getBoundingBox().centerX();
                }
                avgX /= faces.size();

                Log.d(TAG, "angle avg");
                sendDataWithCRC((float)(calculateAngle((int)avgX)));
            }
        }

    }

    protected void sendDataWithCRC(float data) {
        Log.d(TAG, String.format("Angle: %.2f", data));
        ByteBuffer buffer = ByteBuffer.allocate(2 + 3 * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);

        // prefix
        buffer.put(DATA_PREFIX.getBytes());

        // values
        for (int j = 0; j < 1; j++) {
            buffer.putFloat(data);
        }

        byte[] result = buffer.array();
        Log.d(TAG, "Send data for sensor: camera");
        BleUtils.sendDataWithCRC(result, mBleManager, mUartService);
    }

    protected void storeLipMovements(FirebaseVisionFace face) {
        //https://stackoverflow.com/questions/42107466/android-mobile-vision-api-detect-mouth-is-open
        FirebaseVisionPoint lowerLip = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_BOTTOM).getPosition();
        FirebaseVisionPoint upperLip = face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE).getPosition();
        FirebaseVisionPoint rightLip = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_RIGHT).getPosition();
        FirebaseVisionPoint leftLip = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_LEFT).getPosition();
        Rect faceRect = face.getBoundingBox();

        int faceID = face.getTrackingId();

        if (normalizedX.get(faceID)==null) {
            DescriptiveStatistics ds = new DescriptiveStatistics(SLIDING_WINDOW_SIZE);
            normalizedX.put(faceID, ds);
        }

        if (normalizedY.get(faceID)==null) {
            DescriptiveStatistics ds = new DescriptiveStatistics(SLIDING_WINDOW_SIZE);
            normalizedY.put(faceID, ds);
        }

        double normX = (leftLip.getX() - rightLip.getX())/(faceRect.left-faceRect.right);
        normalizedX.get(faceID).addValue(normX);

        double normY = (double)(lowerLip.getY() - upperLip.getY())/(double)(upperLip.getY()-faceRect.top);
        normalizedY.get(faceID).addValue(normY);
    }

    protected double calculateAngle(int x) {
        //int x = face.getBoundingBox().centerX();
        //int y = face.getBoundingBox().centerY();

        int centerX;
        //int centery = imgHeight/2;

        double viewAngle;

        int orientation = mContext.getResources().getConfiguration().orientation;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // In landscape
            centerX = imgWidth/2;
            viewAngle = mCameraSource.getCameraHorizontalViewAngle();
        } else {
            // In portrait
            centerX = imgHeight/2;
            viewAngle = mCameraSource.getCameraVerticalViewAngle();
        }

        return (double)(x-centerX)/(double)centerX*(viewAngle/2.0);
    }

    //stdev-based speaking recognition
    protected double isSpeaking(FirebaseVisionFace face) {
        int faceID = face.getTrackingId();
        DescriptiveStatistics dsX = normalizedX.get(faceID);
        DescriptiveStatistics dsY = normalizedY.get(faceID);

        if (dsX == null || dsY == null || dsX.getN() < SLIDING_WINDOW_SIZE || dsY.getN() < SLIDING_WINDOW_SIZE)
            return 0;

        Log.d(TAG, String.format("DS-StdevY %.5f, meanY %.5f, KurtosisY: %.5f, SkewnessY: %.5f, " +
                        "StdevX %.5f, meanX %.5f, KurtosisX: %.5f, SkewnessX: %.5f",
                dsY.getStandardDeviation(), dsY.getMean(), dsY.getKurtosis(), dsY.getSkewness(),
                dsX.getStandardDeviation(), dsX.getMean(), dsX.getKurtosis(), dsX.getSkewness()));

        if (dsY.getStandardDeviation() + dsX.getStandardDeviation() > 0.030)
            return 1.0;
        else
            return 0.0;
    }

    protected double getRotationDegree(FirebaseVisionFace face) {
            return 0.0;
    }

    @Override
    protected void onFailure(@NonNull Exception e) {
        Log.e(TAG, "Face detection failed " + e);
    }
}
