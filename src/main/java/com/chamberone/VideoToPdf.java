package com.chamberone;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import gusthavo.srt.SRTParser;
import gusthavo.srt.Subtitle;
import net.coobird.thumbnailator.Thumbnails;

/**
 * video to pdf tools
 * 
 * @author gp zhang
 *
 */
public class VideoToPdf {

	/**
	 * generate pdf for video
	 * 
	 * @param videoFilePath
	 * @param subtitleFilePath
	 * @param type
	 * @return pdf file path , return null if failed
	 * @throws IOException
	 */
	public static String convert(String videoFilePath, String subtitleFilePath, Type type) throws IOException {
		if (StringUtils.isBlank(videoFilePath)) {
			return null;
		}
		if (null == type) {
			type = Type.Compound;
		}

		switch (type) {
		case Compound:
			if (StringUtils.isBlank(subtitleFilePath)) {
				return null;
			}
			return genarateFDFFromCompound(videoFilePath, subtitleFilePath);
		case Subtitle:
			if (StringUtils.isBlank(subtitleFilePath)) {
				return null;
			}
			return genarateFDFFromSubtitle(videoFilePath, subtitleFilePath);
		case KeyFrame:
			return genarateFDFFromKeyframe(videoFilePath);
		default:
			break;
		}

		return null;
	}

	private static String genarateFDFFromCompound(String videoFilePath, String subtitleFilePath) throws IOException {
		String pdfName = null;
		ArrayList<Subtitle> subtitles = SRTParser.getSubtitlesFromFile(subtitleFilePath, true);
		try (FFmpegFrameGrabber ff = FFmpegFrameGrabber.createDefault(videoFilePath)) {
			ff.start();
			int frameLength = ff.getLengthInFrames();
			long frameLengthTime = ff.getLengthInTime();
			Queue<Integer> queue = new LinkedList<Integer>();
			Map<Integer, Subtitle> numSubtitleMap = new HashMap<Integer, Subtitle>();
			for (Subtitle subtitle : subtitles) {
				int frame = (int) (frameLength * ((subtitle.timeIn + subtitle.timeOut) / 2)
						/ ((float) frameLengthTime / 1000L));
				queue.add(frame);
				numSubtitleMap.put(frame, subtitle);
			}

			Java2DFrameConverter converter = new Java2DFrameConverter();
			try (PDDocument doc = new PDDocument()) {
				GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
				String[] fontFamilyNames = env.getAvailableFontFamilyNames();
				Font font = new Font(fontFamilyNames[0], Font.BOLD, 40);
				for (int i = 1; i <= frameLength; i++) {
					Integer peek = queue.peek();
					Frame frame = ff.grabImage();
					if (null == peek || null == frame) {
						// 结束
						break;
					}

					if (frame.keyFrame) {
						if (i >= peek) {
							queue.poll();
							BufferedImage bi = converter.getBufferedImage(frame);
							// adjust rotated angle
							int rotate = getRotation(ff);
							if (0 != rotate) {
								bi = Thumbnails.of(bi).rotate(rotate).scale(1).asBufferedImage();
							}

							// add subtitles
							String text = numSubtitleMap.get(i).text;
							Graphics2D g2D = (Graphics2D) bi.getGraphics();
							g2D.setColor(Color.white);
							g2D.setFont(font);
							FontMetrics metrics = g2D.getFontMetrics(font);
							int width = metrics.stringWidth(text);
							g2D.drawString(text, (bi.getWidth() - width) / 2, bi.getHeight() - metrics.getHeight());

							addPage(doc, bi);
						}
					}
				}
				pdfName = FilenameUtils.getBaseName(videoFilePath) + ".pdf";
				doc.save(pdfName);
			}
			ff.stop();
			ff.release();
		}
		return pdfName;
	}

	private static String genarateFDFFromSubtitle(String videoFilePath, String subtitleFilePath) throws IOException {
		String pdfName = null;
		ArrayList<Subtitle> subtitles = SRTParser.getSubtitlesFromFile(subtitleFilePath, true);
		try (FFmpegFrameGrabber ff = FFmpegFrameGrabber.createDefault(videoFilePath)) {
			ff.start();
			int frameLength = ff.getLengthInFrames();
			long frameLengthTime = ff.getLengthInTime();
			Set<Integer> frameNumbers = new HashSet<Integer>();
			Map<Integer, Subtitle> numSubtitleMap = new HashMap<Integer, Subtitle>();
			for (Subtitle subtitle : subtitles) {
				int frame = (int) (frameLength * ((subtitle.timeIn + subtitle.timeOut) / 2)
						/ ((float) frameLengthTime / 1000L));
				frameNumbers.add(frame);
				numSubtitleMap.put(frame, subtitle);
			}

			Java2DFrameConverter converter = new Java2DFrameConverter();
			try (PDDocument doc = new PDDocument()) {
				GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
				String[] fontFamilyNames = env.getAvailableFontFamilyNames();
				Font font = new Font(fontFamilyNames[0], Font.BOLD, 40);
				for (int i = 1; i <= frameLength; i++) {
					if (!frameNumbers.contains(i)) {
						// skip
						ff.grab();
					} else {
						Frame frame = ff.grabImage();
						BufferedImage bi = converter.getBufferedImage(frame);
						// adjust rotated angle
						int rotate = getRotation(ff);
						if (0 != rotate) {
							bi = Thumbnails.of(bi).rotate(rotate).scale(1).asBufferedImage();
						}

						// add subtitles
						String text = numSubtitleMap.get(i).text;
						Graphics2D g2D = (Graphics2D) bi.getGraphics();
						g2D.setColor(Color.white);
						g2D.setFont(font);
						FontMetrics metrics = g2D.getFontMetrics(font);
						int width = metrics.stringWidth(text);
						g2D.drawString(text, (bi.getWidth() - width) / 2, bi.getHeight() - metrics.getHeight());

						addPage(doc, bi);
					}
				}
				pdfName = FilenameUtils.getBaseName(videoFilePath) + ".pdf";
				doc.save(pdfName);
			}
			ff.stop();
			ff.release();
		}
		return pdfName;
	}

	private static String genarateFDFFromKeyframe(String videoFilePath) throws IOException {
		String pdfName = null;
		try (FFmpegFrameGrabber ff = FFmpegFrameGrabber.createDefault(videoFilePath)) {
			ff.start();
			Java2DFrameConverter converter = new Java2DFrameConverter();
			try (PDDocument doc = new PDDocument()) {
				int frameLength = ff.getLengthInFrames();
				for (int i = 1; i <= frameLength; i++) {
					Frame frame = ff.grabImage();
					if (frame.keyFrame) {
						BufferedImage bi = converter.getBufferedImage(frame);
						// adjust rotated angle
						int rotate = getRotation(ff);
						if (0 != rotate) {
							bi = Thumbnails.of(bi).rotate(rotate).scale(1).asBufferedImage();
						}
						addPage(doc, bi);
					}
				}
				pdfName = FilenameUtils.getBaseName(videoFilePath) + ".pdf";
				doc.save(pdfName);
			}
			ff.stop();
			ff.release();
		}
		return pdfName;
	}

	/**
	 * add pdf page, one image per page
	 * 
	 * @param doc
	 * @param image
	 * @throws IOException
	 */
	private static void addPage(PDDocument doc, BufferedImage image) throws IOException {
		PDPage page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
		doc.addPage(page);

		PDImageXObject pdImage = LosslessFactory.createFromImage(doc, image);
		try (PDPageContentStream contents = new PDPageContentStream(doc, page)) {
			// draw the image at full size at (x=0, y=0)
			contents.drawImage(pdImage, 0, 0);
		}
	}

	/**
	 * get rotate angle from FFmpegFrameGrabber
	 * 
	 * @param ffg FFmpegFrameGrabber
	 * @return rotated angle
	 */
	private static int getRotation(FFmpegFrameGrabber ffg) {
		int result = 0;
		// obtain angle
		String rotate = ffg.getVideoMetadata("rotate");
		if (StringUtils.isNotBlank(rotate)) {
			try {
				result = Integer.parseInt(rotate);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return result;
	}

}
