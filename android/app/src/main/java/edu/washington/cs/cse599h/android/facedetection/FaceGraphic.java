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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;
import edu.washington.cs.cse599h.android.camera.GraphicOverlay;
import edu.washington.cs.cse599h.android.camera.GraphicOverlay.Graphic;

import java.util.List;

/**
 * Graphic instance for rendering face position, orientation, and landmarks within an associated
 * graphic overlay view.
 */
public class FaceGraphic extends Graphic {
    private static final float FACE_POSITION_RADIUS = 10.0f;
    private static final float ID_TEXT_SIZE = 40.0f;
    private static final float ID_Y_OFFSET = 50.0f;
    private static final float ID_X_OFFSET = -50.0f;
    private static final float BOX_STROKE_WIDTH = 5.0f;

    private static final int[] COLOR_CHOICES = {
        Color.BLUE, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.RED, Color.WHITE, Color.YELLOW
    };
    private static int currentColorIndex = 0;

    private int facing;

    private final Paint facePositionPaint;
    private final Paint idPaint;
    private final Paint boxPaint;

    private double isSpeakingProb;

    private volatile FirebaseVisionFace firebaseVisionFace;
    private double angleFromCenter;

    public FaceGraphic(GraphicOverlay overlay) {
        super(overlay);

        currentColorIndex = (currentColorIndex + 1) % COLOR_CHOICES.length;
        final int selectedColor = COLOR_CHOICES[currentColorIndex];

        facePositionPaint = new Paint();
        facePositionPaint.setColor(selectedColor);

        idPaint = new Paint();
        idPaint.setColor(selectedColor);
        idPaint.setTextSize(ID_TEXT_SIZE);

        boxPaint = new Paint();
        boxPaint.setColor(selectedColor);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(BOX_STROKE_WIDTH);
    }

    /**
     * Updates the face instance from the detection of the most recent frame. Invalidates the relevant
     * portions of the overlay to trigger a redraw.
     */
    public void updateFace(FirebaseVisionFace face, int facing, double isSpeakingProb, double angle) {
        firebaseVisionFace = face;
        this.facing = facing;
        this.isSpeakingProb = isSpeakingProb;
        this.angleFromCenter = angle;
        postInvalidate();
    }

    /** Draws the face annotations for position on the supplied canvas. */
    @Override
    public void draw(Canvas canvas) {
        FirebaseVisionFace face = firebaseVisionFace;
        if (face == null) {
            return;
        }

        // Draws a circle at the position of the detected face, with the face's track id below.
        float x = translateX(face.getBoundingBox().centerX());
        float y = translateY(face.getBoundingBox().centerY());
        canvas.drawCircle(x, y, FACE_POSITION_RADIUS, facePositionPaint);
        canvas.drawText("id: " + face.getTrackingId(), x + ID_X_OFFSET, y + ID_Y_OFFSET, idPaint);
        canvas.drawText("(" + x +", "+y+")", x + ID_X_OFFSET, y + 2*ID_Y_OFFSET, idPaint);
        //canvas.drawText("(" + face.getBoundingBox().centerX() +", "+face.getBoundingBox().centerY()+")", x + ID_X_OFFSET, y + 3*ID_Y_OFFSET, idPaint);
        canvas.drawText("angle: " + this.angleFromCenter, x + ID_X_OFFSET, y + 3*ID_Y_OFFSET, idPaint);
//        canvas.drawText(
//                "happiness: " + String.format("%.2f", face.getSmilingProbability()),
//                x + ID_X_OFFSET * 3,
//                y - ID_Y_OFFSET,
//                idPaint);
//        if (facing == CameraSource.CAMERA_FACING_FRONT) {
//            canvas.drawText(
//                    "right eye: " + String.format("%.2f", face.getRightEyeOpenProbability()),
//                    x - ID_X_OFFSET,
//                    y,
//                    idPaint);
//            canvas.drawText(
//                    "left eye: " + String.format("%.2f", face.getLeftEyeOpenProbability()),
//                    x + ID_X_OFFSET * 6,
//                    y,
//                    idPaint);
//        } else {
//            canvas.drawText(
//                    "left eye: " + String.format("%.2f", face.getLeftEyeOpenProbability()),
//                    x - ID_X_OFFSET,
//                    y,
//                    idPaint);
//            canvas.drawText(
//                    "right eye: " + String.format("%.2f", face.getRightEyeOpenProbability()),
//                    x + ID_X_OFFSET * 6,
//                    y,
//                    idPaint);
//        }

        if (face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_CHEEK) != null) {
            canvas.drawText(String.format("isSpeaking: %.2f", isSpeakingProb),
                    translateX(face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_CHEEK).getPosition().getX()) + ID_X_OFFSET * 6,
                    translateY(face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_CHEEK).getPosition().getY()),
                    idPaint);
        }

        // Draws a bounding box around the face.
        float xOffset = scaleX(face.getBoundingBox().width() / 2.0f);
        float yOffset = scaleY(face.getBoundingBox().height() / 2.0f);
        float left = x - xOffset;
        float top = y - yOffset;
        float right = x + xOffset;
        float bottom = y + yOffset;
        canvas.drawRect(left, top, right, bottom, boxPaint);

        // draw landmarks
        drawLandmarkPosition(canvas, face, FirebaseVisionFaceLandmark.MOUTH_BOTTOM);
        //drawLandmarkPosition(canvas, face, FirebaseVisionFaceLandmark.LEFT_CHEEK);
        //drawLandmarkPosition(canvas, face, FirebaseVisionFaceLandmark.LEFT_EAR);
        drawLandmarkPosition(canvas, face, FirebaseVisionFaceLandmark.MOUTH_LEFT);
        //drawLandmarkPosition(canvas, face, FirebaseVisionFaceLandmark.LEFT_EYE);
        drawLandmarkPosition(canvas, face, FirebaseVisionFaceLandmark.NOSE_BASE);
        //drawLandmarkPosition(canvas, face, FirebaseVisionFaceLandmark.RIGHT_CHEEK);
        //drawLandmarkPosition(canvas, face, FirebaseVisionFaceLandmark.RIGHT_EAR);
        //drawLandmarkPosition(canvas, face, FirebaseVisionFaceLandmark.RIGHT_EYE);
        drawLandmarkPosition(canvas, face, FirebaseVisionFaceLandmark.MOUTH_RIGHT);

        //drawContour(canvas, face, FirebaseVisionFaceContour.LOWER_LIP_TOP);
        //drawContour(canvas, face, FirebaseVisionFaceContour.UPPER_LIP_BOTTOM);
        //drawContour(canvas, face, FirebaseVisionFaceContour.RIGHT_EYE);
        //drawContour(canvas, face, FirebaseVisionFaceContour.LEFT_EYE);
        //drawContour(canvas, face, FirebaseVisionFaceContour.FACE);
        //drawContour(canvas, face, FirebaseVisionFaceContour.NOSE_BRIDGE);
    }

    private void drawLandmarkPosition(Canvas canvas, FirebaseVisionFace face, int landmarkID) {
        FirebaseVisionFaceLandmark landmark = face.getLandmark(landmarkID);
        if (landmark != null) {
            FirebaseVisionPoint point = landmark.getPosition();
            canvas.drawCircle(
                            translateX(point.getX()),
                            translateY(point.getY()),
                            5f, idPaint);
        }
    }

    private void drawContour(Canvas canvas, FirebaseVisionFace face, int contourID) {
        FirebaseVisionFaceContour contour = face.getContour(contourID);
        if (contour != null) {
            List<FirebaseVisionPoint> points = contour.getPoints();
            for (FirebaseVisionPoint point: points) {
                  canvas.drawCircle(
                        translateX(point.getX()),
                        translateY(point.getY()),
                        5f, idPaint);
            }
        }
    }
}
