/**
 * Copyright 2018 bingoogolapple
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.bingoogolapple.badgeview.compiler;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import cn.bingoogolapple.badgeview.annotation.BGABadge;

/**
 * 作者:王浩 邮件:bingoogolapple@gmail.com
 * 创建时间:2018/1/14
 * 描述:
 */
@SuppressWarnings("unused")
@AutoService(Processor.class)
public class BGABadgeProcessor extends AbstractProcessor {
    private static final String CLASS_JAVA_DOC = "Generated by BGABadgeView-Android. Do not edit it!\n";
    private static final String PACKAGE_NAME = "cn.bingoogolapple.badgeview";
    private static final String CLASS_PREFIX = "BGABadge";
    private Filer mFileUtils;
    private Elements mElementUtils;
    private Messager mMessager;
    private Set<String> mViewClassSet = new HashSet<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        mFileUtils = processingEnv.getFiler();
        mElementUtils = processingEnv.getElementUtils();
        mMessager = processingEnv.getMessager();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * 告知 Processor 哪些注解需要处理
     *
     * @return 返回一个 Set 集合，集合内容为自定义注解的包名+类名
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        final Set<String> annotationTypes = new LinkedHashSet<>();
        annotationTypes.add(BGABadge.class.getCanonicalName());
        return annotationTypes;
    }

    /**
     * 所有的注解处理都是从这个方法开始的，当 APT 找到所有需要处理的注解后，会回调这个方法。当没有属于该 Processor 处理的注解被使用时，不会回调该方法
     *
     * @param set              所有的由该 Processor 处理，并待处理的 Annotations「属于该 Processor 处理的注解，但并未被使用，不存在与这个集合里」
     * @param roundEnvironment 表示当前或是之前的运行环境，可以通过该对象查找到注解
     * @return 表示这组 Annotation 是否被这个 Processor 消费，如果消费「返回 true」后续子的 Processor 不会再对这组 Annotation 进行处理
     */
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        Set<? extends Element> elements = roundEnvironment.getElementsAnnotatedWith(BGABadge.class);
        if (elements == null || elements.isEmpty()) {
            return true;
        }
        mMessager.printMessage(Diagnostic.Kind.NOTE,
                "====================================== BGABadgeProcessor process START ======================================");
        parseParams(elements);
        try {
            generate();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            mMessager.printMessage(Diagnostic.Kind.ERROR, "Exception occurred when generating class file.");
            e.printStackTrace();
        }
        mMessager.printMessage(Diagnostic.Kind.NOTE,
                "====================================== BGABadgeProcessor process END ======================================");
        return true;
    }

    private void parseParams(Set<? extends Element> elements) {
        for (Element element : elements) {
            checkAnnotationValid(element, BGABadge.class);
            TypeElement classElement = (TypeElement) element;
            // 获取该注解的值
            BGABadge badgeAnnotation = classElement.getAnnotation(BGABadge.class);
            String[] classes = badgeAnnotation.value();
            for (String clazz : classes) {
                mViewClassSet.add(clazz);
            }
        }
    }

    private void generate() throws IllegalAccessException, IOException {
        mMessager.printMessage(Diagnostic.Kind.NOTE,"生成 " + mViewClassSet.size() + " 个");
        for (String clazz : mViewClassSet) {
            if (clazz == null || clazz.trim().length() == 0) {
                continue;
            }

            int lastDotIndex = clazz.lastIndexOf(".");
            if (lastDotIndex == -1) {
                String errorMsg = "给 BGABadge 注解传入的参数「" + clazz + "」格式不正确，请传入类的全限定名";
                mMessager.printMessage(Diagnostic.Kind.ERROR, errorMsg);
                throw new RuntimeException(errorMsg);
            }

            String superPackageName = clazz.substring(0, lastDotIndex);
            String superClassName = clazz.substring(lastDotIndex + 1);
            String className = CLASS_PREFIX + superClassName;

            mMessager.printMessage(Diagnostic.Kind.NOTE,clazz + " ====> " + className);

            TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(className)
                    .addJavadoc(CLASS_JAVA_DOC)
                    .addModifiers(Modifier.PUBLIC)
                    .superclass(ClassName.get(superPackageName, superClassName))
                    .addSuperinterface(ClassName.get(PACKAGE_NAME, "BGABadgeable"))
                    .addField(ClassName.get(PACKAGE_NAME, "BGABadgeViewHelper"), "mBadgeViewHelper", Modifier.PRIVATE);

            generateMethod(typeBuilder);

            JavaFile javaFile = JavaFile.builder(PACKAGE_NAME, typeBuilder.build()).build();
            javaFile.writeTo(mFileUtils);
        }
    }

    private void generateMethod(TypeSpec.Builder typeBuilder) {
        constructor(typeBuilder);
        onTouchEvent(typeBuilder);
        callSuperOnTouchEvent(typeBuilder);
        onDraw(typeBuilder);
        dispatchDraw(typeBuilder);
        showCirclePointBadge(typeBuilder);
        showTextBadge(typeBuilder);
        hiddenBadge(typeBuilder);
        showDrawableBadge(typeBuilder);
        setDragDismissDelegate(typeBuilder);
        isShowBadge(typeBuilder);
        getBadgeViewHelper(typeBuilder);
    }

    private void constructor(TypeSpec.Builder typeBuilder) {
        TypeName contextType = ClassName.get("android.content", "Context");
        TypeName attributeSetType = ClassName.get("android.util", "AttributeSet");
        MethodSpec constructorOne = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(contextType, "context")
                .addStatement("this(context, null)")
                .build();
        MethodSpec constructorTwo = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(contextType, "context")
                .addParameter(attributeSetType, "attrs")
                .addStatement("this(context, attrs, 0)")
                .build();
        MethodSpec constructorThree = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(contextType, "context")
                .addParameter(attributeSetType, "attrs")
                .addParameter(int.class, "defStyleAttr")
                .addStatement("super(context, attrs, defStyleAttr)")
                .beginControlFlow("if (android.widget.ImageView.class.isInstance(this) || android.widget.RadioButton.class.isInstance(this))")
                .addStatement("mBadgeViewHelper = new BGABadgeViewHelper(this, context, attrs, BGABadgeViewHelper.BadgeGravity.RightTop)")
                .nextControlFlow("else")
                .addStatement("mBadgeViewHelper = new BGABadgeViewHelper(this, context, attrs, BGABadgeViewHelper.BadgeGravity.RightCenter)")
                .endControlFlow()
                .build();

        typeBuilder.addMethod(constructorOne)
                .addMethod(constructorTwo)
                .addMethod(constructorThree);
    }

    private void onTouchEvent(TypeSpec.Builder typeBuilder) {
        MethodSpec methodSpec = MethodSpec.methodBuilder("onTouchEvent")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get("android.view", "MotionEvent"), "event")
                .addStatement("return mBadgeViewHelper.onTouchEvent(event)")
                .returns(boolean.class)
                .build();
        typeBuilder.addMethod(methodSpec);
    }

    private void callSuperOnTouchEvent(TypeSpec.Builder typeBuilder) {
        MethodSpec methodSpec = MethodSpec.methodBuilder("callSuperOnTouchEvent")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get("android.view", "MotionEvent"), "event")
                .addStatement("return super.onTouchEvent(event)")
                .returns(boolean.class)
                .build();
        typeBuilder.addMethod(methodSpec);
    }

    private void onDraw(TypeSpec.Builder typeBuilder) {
        MethodSpec methodSpec = MethodSpec.methodBuilder("onDraw")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get("android.graphics", "Canvas"), "canvas")
                .addStatement("super.onDraw(canvas)")
                .beginControlFlow("if (!android.view.ViewGroup.class.isInstance(this))")
                .addStatement("mBadgeViewHelper.drawBadge(canvas)")
                .endControlFlow()
                .build();
        typeBuilder.addMethod(methodSpec);
    }

    private void dispatchDraw(TypeSpec.Builder typeBuilder) {
        MethodSpec methodSpec = MethodSpec.methodBuilder("dispatchDraw")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get("android.graphics", "Canvas"), "canvas")
                .addStatement("super.dispatchDraw(canvas)")
                .beginControlFlow("if (android.view.ViewGroup.class.isInstance(this))")
                .addStatement("mBadgeViewHelper.drawBadge(canvas)")
                .endControlFlow()
                .build();
        typeBuilder.addMethod(methodSpec);
    }

    private void showCirclePointBadge(TypeSpec.Builder typeBuilder) {
        MethodSpec methodSpec = MethodSpec.methodBuilder("showCirclePointBadge")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("mBadgeViewHelper.showCirclePointBadge()")
                .build();
        typeBuilder.addMethod(methodSpec);
    }

    private void showTextBadge(TypeSpec.Builder typeBuilder) {
        MethodSpec methodSpec = MethodSpec.methodBuilder("showTextBadge")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(String.class, "badgeText")
                .addStatement("mBadgeViewHelper.showTextBadge(badgeText)")
                .build();
        typeBuilder.addMethod(methodSpec);
    }

    private void hiddenBadge(TypeSpec.Builder typeBuilder) {
        MethodSpec methodSpec = MethodSpec.methodBuilder("hiddenBadge")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("mBadgeViewHelper.hiddenBadge()")
                .build();
        typeBuilder.addMethod(methodSpec);
    }

    private void showDrawableBadge(TypeSpec.Builder typeBuilder) {
        MethodSpec methodSpec = MethodSpec.methodBuilder("showDrawableBadge")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get("android.graphics", "Bitmap"), "bitmap")
                .addStatement("mBadgeViewHelper.showDrawable(bitmap)")
                .build();
        typeBuilder.addMethod(methodSpec);
    }

    private void setDragDismissDelegate(TypeSpec.Builder typeBuilder) {
        MethodSpec methodSpec = MethodSpec.methodBuilder("setDragDismissDelegate")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(PACKAGE_NAME, "BGADragDismissDelegate"), "delegate")
                .addStatement("mBadgeViewHelper.setDragDismissDelegage(delegate)")
                .build();
        typeBuilder.addMethod(methodSpec);
    }

    private void isShowBadge(TypeSpec.Builder typeBuilder) {
        MethodSpec methodSpec = MethodSpec.methodBuilder("isShowBadge")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("return mBadgeViewHelper.isShowBadge()")
                .returns(boolean.class)
                .build();
        typeBuilder.addMethod(methodSpec);
    }

    private void getBadgeViewHelper(TypeSpec.Builder typeBuilder) {
        MethodSpec methodSpec = MethodSpec.methodBuilder("getBadgeViewHelper")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("return mBadgeViewHelper")
                .returns(ClassName.get(PACKAGE_NAME, "BGABadgeViewHelper"))
                .build();
        typeBuilder.addMethod(methodSpec);
    }

    private boolean checkAnnotationValid(Element annotatedElement, Class clazz) {
        if (annotatedElement.getKind() != ElementKind.CLASS) {
            error(annotatedElement, "%s must be declared on class.", clazz.getSimpleName());
            return false;
        }

        if (annotatedElement.getModifiers().contains(Modifier.PRIVATE)) {
            error(annotatedElement, "%s must can not be private.", ((TypeElement)annotatedElement).getQualifiedName());
            return false;
        }
        return true;
    }

    private void error(Element element, String message, Object... args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }
        mMessager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
