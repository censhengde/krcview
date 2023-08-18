# KRC 格式歌词UI控件
## 一，成品展示
krcview 目前仿酷狗音乐app实现了歌词蠕动、滑动定位等功能，效果如下所示：
![image](https://github.com/censhengde/krcview/master/res/krcview.gif)
## 二，使用方式
### 1.在工程根目录 build.gradle 文件添加代码：
```agsl

```
### 2. 在app module build.gradle 文件添加代码：
```agsl

```
### 3. 常用控件属性或api说明:
xml 部分：
```agsl
<com.bytedance.krcview.KrcView
            // 当前行未唱部分歌词颜色。
            app:current_line_text_color="@color/white" 
            // 当前行已唱部分歌词颜色。
            app:current_line_highLight_text_color="@color/design_default_color_secondary_variant"
            //  其他未唱部分歌词颜色。
            app:normal_text_color="@color/white"
            // 歌词行间距
            app:lineSpace="10dp"
            // 最大歌词尺寸
            app:max_text_size="18sp"
            // 最小歌词尺寸
            app:min_text_size="15sp"
            // 单行歌词最大允许字数，超过这个字数则自动换行。
            app:maxWordsPerLine="15"
            // 当前行歌词距离控件顶部的距离。
            app:current_line_top_offset="70dp"
           />
```
java/kotlin代码部分：
```agsl
 // 设置krc 歌词数据。
 public void setKrcData(List<KrcLineInfo> data);
 // 设置当前歌词进度。
 public final void setProgress(final long progress);
 // 设置 LocatedView。note:LocatedView 的垂直中心点与当前行歌词bottom 对齐。
 public void setLocatedView(View view); 
 // 设置拖拽歌词监听器。
 public void setOnDraggingListener(@NonNull onDraggingListener listener);
```
更多api用法详见app module。
## 四，问题反馈渠道
加QQ群：
![image](https://github.com/censhengde/rv-multi-itemtype/blob/master/image/MultiAdapter%E9%97%AE%E9%A2%98%E5%8F%8D%E9%A6%88%E7%BE%A4%E7%BE%A4%E8%81%8A%E4%BA%8C%E7%BB%B4%E7%A0%81.png)
