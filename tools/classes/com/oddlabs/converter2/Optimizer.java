package com.oddlabs.converter2;

import com.oddlabs.util.IndexListOptimizer;
import com.oddlabs.geometry.*;

import org.lwjgl.util.vector.Matrix4f;
import java.util.Map;
import java.nio.ShortBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

public final strictfp class Optimizer {
	private final static float VERTEX_TRESHOLD = 0.000001f;

	private static boolean shortsEquals(int index1, int index2, int size, short[] array1, short[] array2) {
		for (int i = 0; i < size; i++) {
			if (array1[index1*size + i] != array2[index2*size + i]) {
				return false;
			}
		}
		return true;
	}

	private static boolean floatsEquals(int index1, int index2, int size, float[] array1, float[] array2) {
		for (int i = 0; i < size; i++) {
			if (StrictMath.abs(array1[index1*size + i] - array2[index2*size + i]) > VERTEX_TRESHOLD)
				return false;
		}
		return true;
	}

	private static boolean bytesEquals(int index1, int index2, int size, byte[] array1, byte[] array2) {
		for (int i = 0; i < size; i++) {
			if (array1[index1*size + i] != array2[index2*size + i])
				return false;
		}
		return true;
	}

	private static void copyFloats(int index1, int index2, int size, float[] array1, float[] array2) {
		for (int i = 0; i < size; i++) {
            array2[index2*size + i] = array1[index1*size + i];
        }
	}

	private static void copyObjects(int index1, int index2, int size, Object[] array1, Object[] array2) {
		for (int i = 0; i < size; i++) {
            array2[index2*size + i] = array1[index1*size + i];
        }
	}

	private static boolean floatArrayEquals(int index1, int index2, float[][] array1, float[][] array2) {
		return floatsEquals(0, 0, array1[index1].length, array1[index1], array2[index2]);
	}

	private static boolean byteArrayEquals(int index1, int index2, byte[][] array1, byte[][] array2) {
		return bytesEquals(0, 0, array1[index1].length, array1[index1], array2[index2]);
	}

	protected final static ModelInfo optimize(/*String tex_name, */int num_vertices, float[] vertices, float[] normals, float[] colors, float[] uvs, float[] uvs2, byte[][] skin_names, float[][] skin_weights) {
		short[] indices = new short[num_vertices];
		float[] r_vertices = new float[vertices.length];
		float[] r_colors = new float[colors.length];
		float[] r_normals = new float[normals.length];
		float[] r_uvs = new float[uvs.length];
		float[] r_uvs2 = new float[uvs2.length];
		byte[][] r_skin_names = new byte[skin_names.length][];
		float[][] r_skin_weights = new float[skin_weights.length][];

		int index = 0;

		for (int i = 0; i < num_vertices; i++) {
			int j;
			for (j = 0; j < index; j++) {
				boolean equals = floatsEquals(i, j, 3, vertices, r_vertices) &&
								 floatsEquals(i, j, 3, normals, r_normals) &&
								 floatsEquals(i, j, 3, colors, r_colors) &&
								 floatsEquals(i, j, 2, uvs, r_uvs) &&
								 floatsEquals(i, j, 2, uvs2, r_uvs2) &&
								 floatArrayEquals(i, j, skin_weights, r_skin_weights) &&
								 byteArrayEquals(i, j, skin_names, r_skin_names);
				if (equals)
					break;
			}
			if (j == index) {
				copyFloats(i, index, 3, vertices, r_vertices);
				copyFloats(i, index, 3, normals, r_normals);
				copyFloats(i, index, 3, colors, r_colors);
				copyFloats(i, index, 2, uvs, r_uvs);
				copyFloats(i, index, 2, uvs2, r_uvs2);
				copyObjects(i, index, 1, skin_names, r_skin_names);
				copyObjects(i, index, 1, skin_weights, r_skin_weights);
				indices[i] = (short)index;
				index++;
			} else {
				indices[i] = (short)j;
			}
		}
		r_vertices = stripArray(index*3, r_vertices);
		r_normals = stripArray(index*3, r_normals);
		r_colors = stripArray(index*3, r_colors);
		r_uvs = stripArray(index*2, r_uvs);
		r_uvs2 = stripArray(index*2, r_uvs2);
		r_skin_names = stripArray(index, r_skin_names);
		r_skin_weights = stripArray(index, r_skin_weights);
//		System.out.println("resulting vertices = " + index);

		boolean using_texture_coords2 = false;
		for (int i = 0; i < r_uvs2.length; i++) {
            if (r_uvs2[i] != 0f)
                using_texture_coords2 = true;
        }
		if (!using_texture_coords2)
			r_uvs2 = null;
		ShortBuffer index_buffer = ShortBuffer.wrap(indices);
		IndexListOptimizer.optimize(index_buffer);
		return new ModelInfo(/*tex_name,*/ indices, r_vertices, r_normals, r_colors, r_uvs, r_uvs2, r_skin_names, r_skin_weights);
	}

	private static float[][] stripArray(int length, float[][] array) {
		return Arrays.copyOf(array, length);
	}

	private static byte[][] stripArray(int length, byte[][] array) {
		return Arrays.copyOf(array, length);
	}

	private static float[] stripArray(int length, float[] array) {
		return Arrays.copyOf(array, length);
	}

	protected final static SpriteInfo2 convertToSprite(String[][] textures, ModelInfo model_info, float[] clear_color) {
		return new SpriteInfo2(textures, model_info.indices, model_info.vertices, model_info.normals, model_info.texcoords, model_info.texcoords2, model_info.skin_names, model_info.skin_weights, clear_color);
	}

	public final static AnimationInfo convertToAnimation(/*float[] skeleton_vertices,*/ Bone skeleton, Map initial_pose, Map[] anim_map, int type, float wpc) {
		// animations format: [frames] [bones] [matrix]
		int num_frames = anim_map.length;
		float[][] frames = new float[num_frames][];
		for (int frame_index = 0; frame_index < num_frames; frame_index++) {
			float[] bones = new float[initial_pose.size()*12];
			frames[frame_index] = bones;
			normalizeSkeleton(/*new float[]{0, 0, 0}, skeleton_vertices,*/ bones, skeleton, initial_pose, anim_map[frame_index]);
		}
		return new AnimationInfo(frames, type, wpc);
	}

	private static void normalizeSkeleton(/*float[] parent_bone_vertex, float[] skeleton_vertices,*/ float[] bones, Bone current_bone, Map initial_pose_map, Map frame_map) {
		assert initial_pose_map.size() == bones.length/12;
		assert frame_map.size() == bones.length/12;
		String bone_name = current_bone.getName();
		FloatBuffer initial_pose_matrix_buffer = FloatBuffer.wrap((float[])initial_pose_map.get(bone_name));
		FloatBuffer frame_matrix_buffer = FloatBuffer.wrap((float[])frame_map.get(bone_name));
		Matrix4f absolute_initial_pose_matrix = new Matrix4f();
		absolute_initial_pose_matrix.load(initial_pose_matrix_buffer);
		Matrix4f absolute_frame_matrix = new Matrix4f();
		absolute_frame_matrix.load(frame_matrix_buffer);

		Matrix4f inverted_absolute_initial_pose_matrix = new Matrix4f();
		inverted_absolute_initial_pose_matrix.load(absolute_initial_pose_matrix);
		inverted_absolute_initial_pose_matrix.invert();
		Matrix4f resulting_matrix = new Matrix4f();
		Matrix4f.mul(absolute_frame_matrix, inverted_absolute_initial_pose_matrix, resulting_matrix);
		FloatBuffer result_buffer = FloatBuffer.allocate(16);
		resulting_matrix.storeTranspose(result_buffer);
		result_buffer.flip();
		result_buffer.get(bones, current_bone.getIndex()*12, 12);
		final float DELTA = .0001f;
		assert StrictMath.abs(resulting_matrix.m03) < DELTA : resulting_matrix.m03;
		assert StrictMath.abs(resulting_matrix.m13) < DELTA : resulting_matrix.m13;
		assert StrictMath.abs(resulting_matrix.m23) < DELTA : resulting_matrix.m23;
		assert StrictMath.abs(resulting_matrix.m33 - 1f) < DELTA: resulting_matrix.m33;
/*Vector4f bone_point = new Vector4f();
        Vector4f bone_point_transformed = new Vector4f();
        bone_point.set(0, 0, 0, 1);
        Matrix4f.transform(absolute_frame_matrix, bone_point, bone_point_transformed);
        skeleton_vertices[current_bone.getIndex()*6 + 0] = parent_bone_vertex[0];
        skeleton_vertices[current_bone.getIndex()*6 + 1] = parent_bone_vertex[1];
        skeleton_vertices[current_bone.getIndex()*6 + 2] = parent_bone_vertex[2];
        skeleton_vertices[current_bone.getIndex()*6 + 3] = bone_point_transformed.x;
        skeleton_vertices[current_bone.getIndex()*6 + 4] = bone_point_transformed.y;
        skeleton_vertices[current_bone.getIndex()*6 + 5] = bone_point_transformed.z;
        System.out.println("bone_point_transformed.x = " + bone_point_transformed.x + " | bone_point_transformed.y = " + bone_point_transformed.y + " | bone_point_transformed.z = " + bone_point_transformed.z);
        System.out.println(absolute_frame_matrix);*/
        for (Bone child_bone : current_bone.getChildren()) {
            normalizeSkeleton(/*new float[]{bone_point_transformed.x, bone_point_transformed.y, bone_point_transformed.z}, skeleton_vertices,*/ bones, child_bone, initial_pose_map, frame_map);
        }
	}
}
