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

import edu.washington.cs.cse599h.android.ble.BleManager;
import edu.washington.cs.cse599h.android.camera.CameraSource;
import edu.washington.cs.cse599h.android.camera.FrameMetadata;
import edu.washington.cs.cse599h.android.camera.GraphicOverlay;

import java.io.IOException;
import java.nio.ByteBuffer;
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

    SparseArray<LinkedList<FirebaseVisionPoint>> lowerLipArray = new SparseArray<>();
    SparseArray<LinkedList<FirebaseVisionPoint>> upperLipArray = new SparseArray<>();

    protected BleManager mBleManager;
    protected BluetoothGattService mUartService;

    private final int imgWidth = 1280;
    private final int imgHeight = 960;

    public FaceDetectionProcessor(Context context, CameraSource cameraSource, BleManager bleManager, BluetoothGattService uartService) {
        FirebaseVisionFaceDetectorOptions options =
              new FirebaseVisionFaceDetectorOptions.Builder()
                    //.setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                    .setClassificationMode(FirebaseVisionFaceDetectorOptions.NO_CLASSIFICATIONS)
                    .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                    .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
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
        for (int i = 0; i < faces.size(); ++i) {
            FirebaseVisionFace face = faces.get(i);
            FaceGraphic faceGraphic = new FaceGraphic(graphicOverlay);
            graphicOverlay.add(faceGraphic);
            storeLipMovements(face);
            faceGraphic.updateFace(face, frameMetadata.getCameraFacing(), isSpeaking(face), calculateAngle(face));

            if (i == 0) {
                sendDataWithCRC((float)calculateAngle(face));
            }
        }
    }

    protected void sendDataWithCRC(float data) {
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

    protected  void storeLipMovements(FirebaseVisionFace face) {
        //https://stackoverflow.com/questions/42107466/android-mobile-vision-api-detect-mouth-is-open
        List<FirebaseVisionPoint> lowerLip = face.getContour(FirebaseVisionFaceContour.LOWER_LIP_TOP).getPoints();
        List<FirebaseVisionPoint> upperLip = face.getContour(FirebaseVisionFaceContour.UPPER_LIP_BOTTOM).getPoints();

        int faceID = face.getTrackingId();

        if (upperLipArray.get(faceID)==null) {
            LinkedList<FirebaseVisionPoint> q = new LinkedList<>();
            upperLipArray.put(faceID, q);
        }

        if (lowerLipArray.get(faceID)==null) {
            LinkedList<FirebaseVisionPoint> q = new LinkedList<>();
            lowerLipArray.put(faceID, q);
        }

        LinkedList<FirebaseVisionPoint> upperLipSignals = upperLipArray.get(faceID);
        LinkedList<FirebaseVisionPoint> lowerLipSignals = lowerLipArray.get(faceID);

        if (upperLipSignals.size() > 9)
            upperLipSignals.pop();

        if (lowerLipSignals.size() > 9)
            lowerLipSignals.pop();

        if (upperLip.size() > 1 && lowerLip.size() > 1) {
            upperLipSignals.add(upperLip.get(upperLip.size() / 2));
            lowerLipSignals.add(lowerLip.get(lowerLip.size() / 2));
        }
    }

    protected double calculateAngle(FirebaseVisionFace face) {
        int x = face.getBoundingBox().centerX();
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
        LinkedList<FirebaseVisionPoint> upperLipSignals = upperLipArray.get(faceID);
        LinkedList<FirebaseVisionPoint> lowerLipSignals = lowerLipArray.get(faceID);

        if (upperLipSignals == null || lowerLipSignals == null || upperLipSignals.size() < 10 || lowerLipSignals.size() < 10)
            return 0;

        double diffYStd;
        double diffYMean;

        double sum = 0;
        double sum2 = 0;

        Log.d(TAG, "========Diff");
        for (int i=0; i<upperLipSignals.size();i++)
        {
            sum+=(lowerLipSignals.get(i).getY() - upperLipSignals.get(i).getY());
            sum2+=Math.pow((lowerLipSignals.get(i).getY() - upperLipSignals.get(i).getY()), 2);
            Log.d(TAG, String.format("Diff %.2f", (lowerLipSignals.get(i).getY() - upperLipSignals.get(i).getY())));
        }

        diffYMean = sum/upperLipSignals.size();
        double variance = (upperLipSignals.size() * sum2 - sum * sum) / (upperLipSignals.size() * upperLipSignals.size());
        diffYStd = Math.sqrt(variance);
        Log.d(TAG, String.format("Stdev %.2f, mean %.2f", diffYStd, diffYMean));

        if (diffYStd > 10.0)
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
