package it.adrian.code;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import okhttp3.*;
import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.objdetect.CascadeClassifier;

import javax.imageio.ImageIO;
import java.awt.Point;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

public class Main {

    static {
        setLibraryPath(new File("."));
        System.loadLibrary("opencv");

        try {
            ensureModelsExist();
        } catch (IOException e) {
            throw new RuntimeException("Errore nel download dei modelli: " + e.getMessage());
        }
    }

    private static final Map<String, List<Double>> profileScores = new HashMap<>();
    private static final String MODEL_DIR = "models/";
    private static final String AGE_PROTO = MODEL_DIR + "age_deploy.prototxt";
    private static final String AGE_MODEL = MODEL_DIR + "age_net.caffemodel";
    private static final String GENDER_PROTO = MODEL_DIR + "gender_deploy.prototxt";
    private static final String GENDER_MODEL = MODEL_DIR + "gender_net.caffemodel";
    private static final String URL_AGE_PROTO = "https://raw.githubusercontent.com/ChristopherProject/AutoTinderAI/models/age_deploy.prototxt";
    private static final String URL_AGE_MODEL = "https://github.com/ChristopherProject/AutoTinderAI/models/age_net.caffemodel";
    private static final String URL_GENDER_PROTO = "https://raw.githubusercontent.com/ChristopherProject/AutoTinderAI/models/gender_deploy.prototxt";
    private static final String URL_GENDER_MODEL = "https://github.com/ChristopherProject/AutoTinderAI/models/gender_net.caffemodel";
    private static final CascadeClassifier faceDetector = new CascadeClassifier("haarcascade_frontalface_default.xml");
    private static final CascadeClassifier bodyDetector = new CascadeClassifier("haarcascade_fullbody.xml");
    private static final Net ageNet;
    private static final Net genderNet;
    private static double titsExposedPoints, isMaleFaceLikePoint;
    private static String currentProfileFolder = null;
    private static String currentProfileName = "?";
    private static String currentProfileAge = "";
    private static Mat lastFrame = null;

    static {
        ageNet = Dnn.readNetFromCaffe(AGE_PROTO, AGE_MODEL);
        genderNet = Dnn.readNetFromCaffe(GENDER_PROTO, GENDER_MODEL);
    }

    public static void ensureModelsExist() throws IOException {
        Files.createDirectories(Paths.get(MODEL_DIR));
        checkAndDownload(AGE_PROTO, URL_AGE_PROTO);
        checkAndDownload(AGE_MODEL, URL_AGE_MODEL);
        checkAndDownload(GENDER_PROTO, URL_GENDER_PROTO);
        checkAndDownload(GENDER_MODEL, URL_GENDER_MODEL);
    }

    private static void checkAndDownload(String filePath, String url) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            downloadFile(url, filePath);
        }
    }

    private static void downloadFile(String fileURL, String savePath) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(fileURL).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);

        try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(savePath)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        connection.disconnect();
    }

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        File screenshotFolder = new File("screenshots");
        if (!screenshotFolder.exists()) screenshotFolder.mkdir();

        while (true) {
            HWND hwnd = findWindowStartingWith("SM-");
            if (hwnd == null) {
                System.out.println("Nessuna finestra trovata. Ritento...");
                Thread.sleep(2000);
                continue;
            }
            RECT rect = new RECT();
            User32.INSTANCE.GetWindowRect(hwnd, rect);
            int width = rect.right - rect.left;
            int height = rect.bottom - rect.top;

            lastFrame = null;

            BufferedImage capture = robot.createScreenCapture(new Rectangle(rect.left, rect.top, width, height));
            Mat frame = bufferedImageToMat(capture);

            Rect[] faces = detectObjects(frame, faceDetector);
            Rect[] bodies = detectObjects(frame, bodyDetector);

            System.out.println("Faces detected: " + faces.length);
            System.out.println("Bodies detected: " + bodies.length);

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

            //fist
            pressScreenCenter(robot, rect);
            Thread.sleep(2000);

            String profileName = null;
            String profileAge = null;
            int retries = 3;

            while ((profileName == null || profileName.matches("\\d+")) || (profileAge == null || !profileAge.matches("\\d{2}"))) {
                String[] nameAge = extractNameAndAge(capture);
                profileName = nameAge[1];
                profileAge = nameAge[0];

                // Se il nome estratto è solo numerico o non valido, riprovare
                if (profileName != null && profileName.matches("\\d+")) {
                    profileName = null;
                }

                if (profileAge == null || !profileAge.matches("\\d{2}") || Integer.parseInt(profileAge) < 18 || Integer.parseInt(profileAge) > 99) {
                    profileAge = null;
                    retries--;
                    if (retries <= 0) break;
                }

                Thread.sleep(1000);
            }

            if (profileAge == null) profileAge = "unknown";

            System.out.println("Detected: Nome = " + profileName + ", Età = " + profileAge);

            String profileFolder = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + "_" + profileName + "_" + profileAge;
            File profileDir = new File("screenshots/" + profileFolder);
            profileDir.mkdirs();


            currentProfileFolder = profileFolder;
            currentProfileName = profileName;
            currentProfileAge = profileAge;//hard path

            if(currentProfileAge.isEmpty())continue;

            int screenshotCount = profileScores.getOrDefault(currentProfileFolder, new ArrayList<>()).size();
            String filename = "screenshots/" + currentProfileFolder + "/detection_" + screenshotCount + "_" + timestamp + ".png";

            Imgcodecs.imwrite(filename, frame);

            List<Detection> puryDetections = fetchDetections(new File(filename));
            double baseScore = evaluateBeauty(frame, faces);
            double puryPoints = puryDetections.size() + 4;
            double bonus = (faces.length > 0) ? 3 : (bodies.length > 0) ? 3 : -3;
            double totalPoints = (baseScore + puryPoints + bonus + titsExposedPoints) - isMaleFaceLikePoint;//tits are more important ;)

            System.out.println("nsfw detections: " + puryDetections.size());

            titsExposedPoints = 0;
            isMaleFaceLikePoint = 0;

            drawDetections(frame, faces, new Scalar(0, 255, 0));
            drawDetections(frame, bodies, new Scalar(255, 0, 0));
            drawPuryfiDetections(frame, puryDetections);

            Imgcodecs.imwrite(filename, frame);

            Thread.sleep(1000);

            profileScores.computeIfAbsent(currentProfileFolder, k -> new ArrayList<>()).add(totalPoints);//media of points

            for (int i = 0; i < 3; i++) {
                pressScreenCenter(robot, rect);
                Thread.sleep(2000);

                BufferedImage newCapture = robot.createScreenCapture(new Rectangle(rect.left, rect.top, width, height));
                Mat newFrame = bufferedImageToMat(newCapture);
                lastFrame = newFrame;
                screenshotCount++;
                String newFilename = "screenshots/" + currentProfileFolder + "/detection_" + screenshotCount + "_" + timestamp + ".png";

                Imgcodecs.imwrite(newFilename, newFrame);

                faces = detectObjects(newFrame, faceDetector);
                bodies = detectObjects(newFrame, bodyDetector);

                List<Detection> newPuryDetections = fetchDetections(new File(newFilename));

                drawDetections(newFrame, faces, new Scalar(0, 255, 0));
                drawDetections(newFrame, bodies, new Scalar(255, 0, 0));
                drawPuryfiDetections(newFrame, newPuryDetections);

                Imgcodecs.imwrite(newFilename, newFrame);
            }

            System.out.println("Girl Beautifulest Score: " + totalPoints);

            if (totalPoints >= 9) {//9 or 5
                System.out.println("Like");
                clickColorButton(frame, robot, rect, new Scalar(50, 100, 100), new Scalar(80, 255, 255), "like");

            } else {
                System.out.println("Dislike");
                clickColorButton(frame, robot, rect, new Scalar(0, 100, 100), new Scalar(10, 255, 255), "dislike");
            }

            finalizeProfile(currentProfileFolder);
            currentProfileName = null;
            currentProfileAge = null;
            currentProfileFolder = null;

            Thread.sleep(3000);
        }
    }


    private static HWND findWindowStartingWith(String prefix) {
        final HWND[] result = {null};
        User32.INSTANCE.EnumWindows((hWnd, data) -> {
            char[] windowText = new char[512];
            User32.INSTANCE.GetWindowText(hWnd, windowText, 512);
            String wText = Native.toString(windowText);
            if (wText.startsWith(prefix)) {
                result[0] = hWnd;
                return false;
            }
            return true;
        }, null);
        return result[0];
    }

    private static void drawDetections(Mat image, Rect[] detections, Scalar color) {
        for (Rect rect : detections)
            Imgproc.rectangle(image, new org.opencv.core.Point(rect.x, rect.y), new org.opencv.core.Point((rect.x + rect.width), (rect.y + rect.height)), color, 3);
    }

    private static void drawPuryfiDetections(Mat image, List<Detection> detections) {
        for (Detection d : detections)
            Imgproc.rectangle(image, new org.opencv.core.Point(d.boundingBox[0], d.boundingBox[1]), new org.opencv.core.Point(d.boundingBox[2], d.boundingBox[3]), new Scalar(0.0D, 0.0D, 255.0D), 3);
    }


    private static Rect[] detectObjects(Mat image, CascadeClassifier classifier) {
        MatOfRect objects = new MatOfRect();
        classifier.detectMultiScale(image, objects, 1.1D, 5, 0, new Size(50.0D, 50.0D), new Size());
        return objects.toArray();
    }

    private static void pressScreenCenter(Robot robot, RECT rect) {
        int centerX = (rect.left + rect.right) / 2 + 100;
        int centerY = (rect.top + rect.bottom) / 2;
        moveMouseSmooth(robot, centerX, centerY);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(50);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void finalizeProfile(String profileFolder) {
        List<Double> scores = profileScores.get(profileFolder);
        if (scores == null || scores.isEmpty()) {
            System.out.println("Nessun punteggio registrato per " + profileFolder);
            return;
        }
        double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        System.out.println("---> Profilo " + profileFolder + ": media punteggio = " + avg);
    }

    private static void moveMouseSmooth(Robot robot, int x, int y) {
        Point current = MouseInfo.getPointerInfo().getLocation();
        double steps = 50;
        double dx = (x - current.x) / steps;
        double dy = (y - current.y) / steps;
        for (int step = 0; step < steps; step++) {
            robot.mouseMove((int) (current.x + dx * step), (int) (current.y + dy * step));
            robot.delay(10);
        }
    }

    private static void clickColorButton(Mat image, Robot robot, RECT windowRect, Scalar lowerHSV, Scalar upperHSV, String buttonName) {
        int yStart = (int) (image.rows() * 0.75);
        int yEnd = (int) (image.rows() * 0.9);

        int xStart, xEnd;
        if ("like".equals(buttonName)) {
            xStart = (int) (image.cols() * 0.55);
            xEnd = (int) (image.cols() * 0.8);
        } else {
            xStart = (int) (image.cols() * 0.2);
            xEnd = (int) (image.cols() * 0.45);
        }

        Rect roiRect = new Rect(xStart, yStart, xEnd - xStart, yEnd - yStart);
        Mat roi = new Mat(image, roiRect);

        Mat hsv = new Mat();
        Imgproc.cvtColor(roi, hsv, Imgproc.COLOR_BGR2HSV);

        Mat mask = new Mat();
        Core.inRange(hsv, lowerHSV, upperHSV, mask);

        Moments moments = Imgproc.moments(mask);
        if (moments.m00 > 0) {
            int x = (int) (moments.m10 / moments.m00) + xStart;
            int y = (int) (moments.m01 / moments.m00) + yStart;

            int screenX = windowRect.left + x;
            int screenY = windowRect.top + y;

            moveMouseSmooth(robot, screenX, screenY);

            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(25);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }
    }

    private static Mat bufferedImageToMat(BufferedImage bi) {
        if (bi.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            BufferedImage convertedImg = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = convertedImg.createGraphics();
            g.drawImage(bi, 0, 0, null);
            g.dispose();
            bi = convertedImg;
        }
        Mat mat = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC3);
        byte[] data = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
        mat.put(0, 0, data);
        return mat;
    }

    private static List<Detection> fetchDetections(File imageFile) throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", imageFile.getName(), RequestBody.create(MediaType.parse("application/octet-stream"), imageFile)).build();
        Request request = new Request.Builder().url("http://pury.fi/detect").post(body).build();
        Response response = client.newCall(request).execute();
        String responseBody = response.body().string();
        response.close();
        return parseDetections(responseBody);
    }

    private static List<Detection> parseDetections(String jsonResponse) {
        List<Detection> detections = new ArrayList<>();
        JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
        JsonObject outputObj = jsonObject.getAsJsonObject("output");
        if (outputObj == null) {
            return detections;
        }
        JsonArray detectionsArray = outputObj.getAsJsonArray("detections");
        if (detectionsArray == null) {
            return detections;
        }
        for (JsonElement element : detectionsArray) {
            JsonObject detectionObject = element.getAsJsonObject();
            String name = detectionObject.get("name").getAsString();
            if (!name.equals("ARMPITS_EXPOSED")) {
                JsonArray bboxArray = detectionObject.getAsJsonArray("bounding_box");
                double[] bbox = new double[]{bboxArray.get(0).getAsDouble(), bboxArray.get(1).getAsDouble(), bboxArray.get(2).getAsDouble(), bboxArray.get(3).getAsDouble()};
                if (name.equals("FEMALE_BREAST_EXPOSED") || name.equals("FEMALE_BREAST_COVERED")) {
                    titsExposedPoints = 5;
                }
                if (name.equals("MALE_FACE")) {
                    isMaleFaceLikePoint = 10;
                }
                detections.add(new Detection(bbox));
            }
        }
        return detections;
    }

    private static double evaluateBeauty(Mat frame, Rect[] faces) {
        if (faces.length == 0) return 0;
        Rect faceRect = faces[0];
        Mat faceROI = new Mat(frame, faceRect);
        Mat blob = Dnn.blobFromImage(faceROI, 1.0, new Size(227, 227), new Scalar(78.426, 87.768, 114.895), false, false);
        ageNet.setInput(blob);
        Mat agePreds = ageNet.forward();
        int ageIndex = (int) Core.minMaxLoc(agePreds.reshape(1, 1)).maxLoc.x;
        boolean isYoung = (ageIndex <= 3);
        genderNet.setInput(blob);
        Mat genderPreds = genderNet.forward();
        int genderIndex = (int) Core.minMaxLoc(genderPreds.reshape(1, 1)).maxLoc.x;
        boolean isFemale = (genderIndex == 1);
        double ratio = (double) faceRect.width / faceRect.height;
        double score = 0;
        if (isYoung) score += 5;
        if (isFemale) score += 3;
        if (ratio < 0.9) score += 5;
        else score -= 3;
        return score;
    }

    private static String[] extractNameAndAge(BufferedImage bufferedImage) throws IOException, TesseractException {
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
        tesseract.setLanguage("ita");

        File tempFile = new File("temp_ocr.png");
        if(tempFile.exists()) {
            tempFile.delete();
        }

        int yStart = (int) ((bufferedImage.getHeight() * 0.60));//+ 3); //image percentage from bottom
        BufferedImage croppedImage = bufferedImage.getSubimage(0, yStart, bufferedImage.getWidth(), bufferedImage.getHeight() - yStart);

        ImageIO.write(croppedImage, "png", tempFile);

        String ocrResult = tesseract.doOCR(tempFile);
        String cleanedText = normalizeText(ocrResult);
        String[] words = cleanedText.split("\\s+");

        String name = null;
        String age = null;

        for (int i = 0; i < words.length; i++) {
            if (words[i].matches("\\d{2}")) { // valid age?
                int detectedAge = Integer.parseInt(words[i]);
                if (detectedAge >= 18 && detectedAge <= 99) {
                    age = String.valueOf(detectedAge);
                    if (i > 0 && words[i - 1].length() > 2 && words[i - 1].matches("[A-Za-z]+")) {
                        name = words[i - 1];
                    }
                }
            }
        }

        if (name == null || name.matches(".*\\d.*") || name.length() <= 2) {
            for (String word : words) {
                if (word.matches("[A-Za-z]+") && word.length() > 2) {
                    name = word;
                    break;
                }
            }
        }

        int retries = 3;
        while ((name == null || name.matches(".*\\d.*") || name.length() <= 2) && retries > 0) {
            System.out.println("OCR fallito, riprovo...");
            retries--;

            ImageIO.write(applyContrastEnhancement(croppedImage), "png", tempFile);
            ocrResult = tesseract.doOCR(tempFile);
            cleanedText = normalizeText(ocrResult);
            words = cleanedText.split("\\s+");

            for (String word : words) {
                if (word.matches("[A-Za-z]+") && word.length() > 2) {
                    name = word;
                    break;
                }
            }
        }

        if (name == null) name = "Sconosciuto";
        if (age == null) age = "??";

        tempFile.delete();
        return new String[]{age, name};
    }

    private static BufferedImage applyContrastEnhancement(BufferedImage img) {
        BufferedImage result = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();
        return result;
    }

    public static String normalizeText(String input) {
        String cleaned = input.replaceAll("[^a-zA-Z0-9 ]", "");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("\\b[a-zA-Z]{1,2}\\b", "").replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private static void setLibraryPath(File relativePath) {
        System.setProperty("java.library.path", relativePath.getAbsolutePath());
    }

    static class Detection {
        double[] boundingBox;

        public Detection(double[] boundingBox) {
            this.boundingBox = boundingBox;
        }
    }
}
