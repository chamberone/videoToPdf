# videoToPdf

  Too much videos for kids? try pdf pic books. Safer for the eyes and be aggressive reader.

## Usage
~~~java
    VideoToPdf.convert("test.mp4", "test.srt", Type.Subtitle);
~~~

The third param has three options : 
1. **Compound** form pages accourding to keyframe+subtitle.
2. **Subtitle** form pages accourding to video subtitles.
3. **KeyFrame**  form pages accourding to video keyframe.
