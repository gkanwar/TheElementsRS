package com.idkjava.thelements.rs;

import android.content.Context;
import android.content.res.Resources;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.Type;
import android.util.AttributeSet;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Renders game surface using Open GL
 */
public class SandView extends GLSurfaceView {

    SandViewRenderer mRend;

    //Constructor
    public SandView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        setEGLContextClientVersion(2);
        mRend = new SandViewRenderer(); //Set up the Renderer for the View
        setEGLConfigChooser(8, 8, 8, 8, // RGBA channel bits
                16, 0); // depth and stencil channel min bits
        setRenderer(mRend); //Associate it with this view
    }

    public void setRS(RenderScript rs) {
        mRend.setRS(rs, getContext().getResources());
    }
}

class SandViewRenderer implements GLSurfaceView.Renderer
{
    private final String vertexShaderCode =
            "attribute vec4 vPosition;" +
            "attribute vec2 aTexCoord;" +
            "varying vec2 vTexCoord;" +
            "void main() {" +
            "  vTexCoord = aTexCoord;" +
            "  gl_Position = vPosition;" +
            "}";

    private final String fragmentShaderCode =
            "precision mediump float;" +
            "uniform sampler2D uTexture;" +
            "varying vec2 vTexCoord;" +
            "void main() {" +
            "  gl_FragColor = texture2D(uTexture, vTexCoord);" +
            "}";
    private int mProgram;
    private int mPosHandle;
    private int mTexCoordHandle;
    private int mTexUniformHandle;

    public static int loadShader(int type, String shaderCode){

        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        int shader = GLES20.glCreateShader(type);

        // add the source code to the shader and compile it
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        return shader;
    }

    private float[] mVertArr = {-1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f};
    private float[] mColor = {1.0f, 0.0f, 0.0f, 1.0f};
    private static final int FLOAT_SIZE = 4;
    private FloatBuffer mVerts;
    private FloatBuffer mTexCoords;

    private int texHandle;
    private int texWidth;
    private int texHeight;
    private int screenWidth;
    private int screenHeight;
    private int workWidth;
    private int workHeight;

    private ByteBuffer texBuffer;
    private static final int NUM_CHANNELS = 4;

    private RenderScript mRS;
    private ScriptC_elements mScript;
    private Element u32;
    private Allocation mAllCoords;

    public void setRS(RenderScript rs, Resources res) {
        mRS = rs;
        mScript = new ScriptC_elements(mRS, res, R.raw.elements);
        u32 = Element.U32(rs);

        // Build the elements array
        ScriptField_Element elements = new ScriptField_Element(mRS, 256);
        mScript.bind_elements(elements);
        ScriptField_Particle particles = new ScriptField_Particle(mRS, 100);
        mScript.bind_particles(particles);
    }

    public void onSurfaceCreated(GL10 unused, EGLConfig config)
    {
        // Init GL
        GLES20.glClearColor(0.2f, 0.2f, 0.2f, 1.0f);
        mProgram = GLES20.glCreateProgram();
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragmentShader);
        GLES20.glLinkProgram(mProgram);
        mPosHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        mTexCoordHandle= GLES20.glGetAttribLocation(mProgram, "aTexCoord");
        mTexUniformHandle = GLES20.glGetUniformLocation(mProgram, "uTexture");

        // Init geometry
        ByteBuffer buf = ByteBuffer.allocateDirect(mVertArr.length * FLOAT_SIZE);
        buf.order(ByteOrder.nativeOrder());
        mVerts = buf.asFloatBuffer();
        mVerts.put(mVertArr);
        mVerts.position(0);

        buf = ByteBuffer.allocateDirect(mVertArr.length * FLOAT_SIZE);
        buf.order(ByteOrder.nativeOrder());
        mTexCoords = buf.asFloatBuffer();

        // Init texture
        int[] handles = new int[1];
        GLES20.glGenTextures(1, handles, 0);
        texHandle = handles[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texHandle);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
    }

    public void onSurfaceChanged(GL10 unused, int w, int h)
    {
        final int ZOOM = 4;
        screenWidth = w;
        screenHeight = h;
        workWidth = w/ZOOM;
        workHeight = h/ZOOM;
        Log.d("SandView", screenWidth + "x" + screenHeight + " -> " + workWidth + "x" + workHeight);

        // Resize
        GLES20.glViewport(0, 0, w, h);
        // Find next pow of two greater
        texWidth = 1;
        while (texWidth < workWidth) {
            texWidth *= 2;
        }
        texHeight = 1;
        while (texHeight < workHeight) {
            texHeight *= 2;
        }
        Log.d("SandView", "tex: " + texWidth + "x" + texHeight);
        texBuffer = ByteBuffer.allocateDirect(texWidth*texHeight*NUM_CHANNELS);
        texBuffer.order(ByteOrder.nativeOrder());

        // Testing buffer data
        for (int i = 0; i < texHeight; ++i) {
            for (int  j = 0; j < texWidth; ++j) {
                if ((i+j)%2 == 0) {
                    int ind = NUM_CHANNELS * (i * texWidth + j);
                    texBuffer.put(ind, (byte) 0xff);
                    texBuffer.put(ind + 1, (byte) 0xff);
                    texBuffer.put(ind + 2, (byte) 0xff);
                    texBuffer.put(ind + 3, (byte) 0xff);
                }
            }
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texHandle);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, texWidth, texHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, texBuffer);

        // Update tex coords
        float texW = workWidth/(float)texWidth;
        float texH = workHeight/(float)texHeight;
        float[] texCoordArr = {
                0.0f, 0.0f,
                0.0f, texH,
                texW, texH,
                0.0f, 0.0f,
                texW, texH,
                texW, 0.0f
        };
        mTexCoords.put(texCoordArr);
        mTexCoords.position(0);

        // Update RS allocations
        Type.Builder acTypeBuilder = new Type.Builder(mRS, u32);
        acTypeBuilder.setX(workWidth);
        acTypeBuilder.setY(workHeight);
        mAllCoords = Allocation.createTyped(mRS, acTypeBuilder.create());
        mScript.set_allCoords(mAllCoords);

        // Update RS globals
        mScript.set_workWidth(workWidth);
        mScript.set_workHeight(workHeight);
    }

    public void onDrawFrame(GL10 unused)
    {
        // Physics update


        // Render frame
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(mProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texHandle);
        GLES20.glUniform1i(mTexUniformHandle, 0);
        GLES20.glEnableVertexAttribArray(mPosHandle);
        GLES20.glEnableVertexAttribArray(mTexCoordHandle);
        GLES20.glVertexAttribPointer(mPosHandle, 2, GLES20.GL_FLOAT, false, 0, mVerts);
        GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mTexCoords);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDisableVertexAttribArray(mPosHandle);
        GLES20.glDisableVertexAttribArray(mTexCoordHandle);
    }
}
