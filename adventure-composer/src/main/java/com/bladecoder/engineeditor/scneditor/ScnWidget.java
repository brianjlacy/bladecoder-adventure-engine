/*******************************************************************************
 * Copyright 2014 Rafael Garcia Moreno.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.bladecoder.engineeditor.scneditor;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.MessageFormat;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable;
import com.bladecoder.engine.anim.AnimationDesc;
import com.bladecoder.engine.anim.Tween;
import com.bladecoder.engine.assets.AssetConsumer;
import com.bladecoder.engine.assets.EngineAssetManager;
import com.bladecoder.engine.model.ActorRenderer;
import com.bladecoder.engine.model.BaseActor;
import com.bladecoder.engine.model.InteractiveActor;
import com.bladecoder.engine.model.Scene;
import com.bladecoder.engine.model.SceneLayer;
import com.bladecoder.engine.model.SpriteActor;
import com.bladecoder.engine.util.RectangleRenderer;
import com.bladecoder.engineeditor.Ctx;
import com.bladecoder.engineeditor.model.BaseDocument;
import com.bladecoder.engineeditor.model.ChapterDocument;
import com.bladecoder.engineeditor.model.Project;
import com.bladecoder.engineeditor.utils.EditorLogger;

public class ScnWidget extends Widget {
	private static final Color BLACK_TRANSPARENT = new Color(0f, 0f, 0f, 0.5f);

	// TMPs to avoid GC calls
	private final Vector3 tmpV3 = new Vector3();
	private final Vector2 tmpV2 = new Vector2();
	private final Vector2 tmp2V2 = new Vector2();

	private final SpriteBatch sceneBatch = new SpriteBatch();
	private final CanvasDrawer drawer = new CanvasDrawer();
	private final SpriteDrawer faRenderer = new SpriteDrawer();
	private final ScnWidgetInputListener inputListner = new ScnWidgetInputListener(this);

	private final Rectangle bounds = new Rectangle();
	private final Rectangle scissors = new Rectangle();

	private Scene scn;
	private BaseActor selectedActor = null;
	private boolean inScene = false;
	private boolean animation = true;

	private static final int[] zoomLevels = { 5, 10, 16, 25, 33, 50, 66, 100, 150, 200, 300, 400, 600, 800, 1000 };
	private int zoomLevel = 100;

	private BitmapFont bigFont;
	private BitmapFont defaultFont;
	private TiledDrawable tile;

	private boolean loading = false;
	private boolean loadingError = false;

	private WalkZoneWindow walkZoneWindow;

	private boolean showWalkZone;

	private final GlyphLayout textLayout = new GlyphLayout();

	private final OrthographicCamera camera = new OrthographicCamera();

	/**
	 * The NOTIFY_PROJECT_LOADED listener is called from other thread. This flag
	 * is to recreate the scene in the OpenGL thread.
	 */
	private boolean projectLoadedFlag = false;

	public ScnWidget(Skin skin) {
		bigFont = skin.get("big-font", BitmapFont.class);
		defaultFont = skin.get("default-font", BitmapFont.class);

		setSize(150, 150);

		tile = new TiledDrawable(Ctx.assetManager.getIcon("transparent-light"));

		faRenderer.setViewport(getWidth(), getHeight());

		setLayoutEnabled(true);

		addListener(inputListner);

		Ctx.project.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent e) {
				EditorLogger.debug("ScnWidget (Project Listener): " + e.getPropertyName());

				if (e.getPropertyName().equals(Project.NOTIFY_SCENE_SELECTED)) {
					if (!projectLoadedFlag)
						setSelectedScene(Ctx.project.getSelectedScene());
				} else if (e.getPropertyName().equals(Project.NOTIFY_ACTOR_SELECTED)) {
					if (!projectLoadedFlag)
						setSelectedActor(Ctx.project.getSelectedActor());
				} else if (e.getPropertyName().equals(Project.NOTIFY_ANIM_SELECTED)) {
					if (!projectLoadedFlag)
						setSelectedFA(Ctx.project.getSelectedFA());
				} else if (e.getPropertyName().equals(Project.NOTIFY_PROJECT_LOADED)) {
					projectLoadedFlag = true;
				}
			}
		});

		Ctx.project.getWorldDocument().addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent e) {
				EditorLogger.debug("ScnWidget (World Listener): " + e.getPropertyName());
				ChapterDocument doc = Ctx.project.getSelectedChapter();

				if (e.getPropertyName().equals("scene")) {
					setSelectedScene(Ctx.project.getSelectedScene());
					setSelectedActor(Ctx.project.getSelectedActor());
				} else if (e.getPropertyName().equals("bbox")) {
					// TODO DELETE
//					// Element selActor = (Element) e.getNewValue();
//					Element selActor = Ctx.project.getSelectedChapter().getActor(
//							Ctx.project.getSelectedChapter().getSceneById(Ctx.project.getSelectedScene().getId()),
//							Ctx.project.getSelectedActor().getId());
//					String id = doc.getId(selActor);
//					BaseActor a = scn.getActor(id, false);
//
//					if (a == null)
//						return;
//
//					doc.getBBox(a.getBBox(), selActor);
//					Vector2 p = doc.getPos(selActor);
//					a.setPosition(p.x, p.y);
//
//					if (a instanceof SpriteActor) {
//						SpriteActor sa = (SpriteActor) a;
//						if (!selActor.getAttribute(XMLConstants.BBOX_ATTR).isEmpty())
//							sa.setBboxFromRenderer(false);
//						else
//							sa.setBboxFromRenderer(true);
//					}

				} else if (e.getPropertyName().equals("pos")) {
					Element selActor = (Element) e.getNewValue();
					String id = doc.getId(selActor);
					BaseActor a = scn.getActor(id, false);
					if (a == null)
						return;
					Vector2 p = doc.getPos(selActor);
					a.setPosition(p.x, p.y);
				} else if (e.getPropertyName().equals("id")) {
					String id = (String) e.getOldValue();

					if (selectedActor == null || !selectedActor.getId().equals(id))
						return;

					scn.removeActor(scn.getActor(id, false));
					setSelectedActor(null);
				} else if (e.getPropertyName().equals("animation")) {
					createAndSelectActor(Ctx.project.getSelectedActor());
					setSelectedFA(null);
				} else if (e.getPropertyName().equals("init_animation")) {
					String initFA = (String) e.getNewValue();
					((SpriteActor) selectedActor).getRenderer().setInitAnimation(initFA);
					setSelectedFA(null);
				} else if (e.getPropertyName().equals("actor")) {
					createAndSelectActor((BaseActor) e.getNewValue());
				} else if (e.getPropertyName().equals("layer")) {
					Element el = (Element) e.getNewValue();
					String name = el.getAttribute("id");
					NodeList layersNodes = ((Element) el.getParentNode()).getElementsByTagName("layer");
					boolean visible = Boolean.parseBoolean(el.getAttribute("visible"));
					boolean dynamic = Boolean.parseBoolean(el.getAttribute("dynamic"));
					// Element previousSibling =
					// (Element)el.getPreviousSibling();

					SceneLayer layer = scn.getLayer(name);

					if (layer == null && layersNodes.getLength() > scn.getLayers().size()) {
						// NEW LAYER CREATED
						layer = new SceneLayer();
						layer.setName(name);
						layer.setVisible(visible);
						layer.setDynamic(dynamic);

						scn.addLayer(layer);
					} else if (layer.isDynamic() != dynamic) {
						layer.setDynamic(dynamic);
					} else if (layer.isVisible() != visible) {
						layer.setVisible(visible);
					} else {
						// TODO Handle order and id change
						setSelectedScene(Ctx.project.getSelectedScene());
						setSelectedActor(Ctx.project.getSelectedActor());
					}

				} else if (e.getPropertyName().equals(BaseDocument.NOTIFY_ELEMENT_DELETED)) {
					if (((Element) e.getNewValue()).getTagName().equals("actor"))
						removeActor(doc, (Element) e.getNewValue());
					else if (((Element) e.getNewValue()).getTagName().equals("animation"))
						setSelectedFA(null);
					else if (((Element) e.getNewValue()).getTagName().equals("layer")) {
						setSelectedScene(Ctx.project.getSelectedScene());
						setSelectedActor(Ctx.project.getSelectedActor());
					}
				}
			}
		});

		walkZoneWindow = new WalkZoneWindow(skin, inputListner);
	}

	public OrthographicCamera getCamera() {
		return camera;
	}

	@Override
	public void act(float delta) {
		if (projectLoadedFlag) {
			projectLoadedFlag = false;

			if (scn != null) {
				scn.dispose();
				scn = null;
			}

			setSelectedScene(Ctx.project.getSelectedScene());
			setSelectedActor(Ctx.project.getSelectedActor());
			setSelectedFA(Ctx.project.getSelectedFA());
		}

		if (scn != null && !loading && !loadingError) {
			if (!inScene)
				faRenderer.update(delta);
			// scn.update(delta);

			for (SceneLayer layer : scn.getLayers())
				layer.update();

			if (animation) {
				for (BaseActor a : scn.getActors().values()) {
					a.update(delta);
				}
			}

			handleKeyPositioning();
		}
	}

	private void handleKeyPositioning() {
		if (Gdx.input.isKeyPressed(Keys.UP) || Gdx.input.isKeyPressed(Keys.DOWN) || Gdx.input.isKeyPressed(Keys.LEFT)
				|| Gdx.input.isKeyPressed(Keys.RIGHT)) {

			BaseActor selActor = getSelectedActor();
			Polygon p = selActor.getBBox();
			// undoOrg.set(p.getX(), p.getY());

			if (Gdx.input.isKeyPressed(Keys.UP))
				p.translate(0, 1);
			else if (Gdx.input.isKeyPressed(Keys.DOWN))
				p.translate(0, -1);
			else if (Gdx.input.isKeyPressed(Keys.LEFT))
				p.translate(-1, 0);
			else if (Gdx.input.isKeyPressed(Keys.RIGHT))
				p.translate(1, 0);

			Ctx.project.getSelectedChapter().setModified(selActor);

			// undoOp = new UndoSetAttr(Ctx.project.getSelectedChapter(),
			// Ctx.project.getSelectedActor(), "pos",
			// Param.toStringParam(undoOrg));
			// Ctx.project.getUndoStack().add(undoOp);
		}
	}

	@Override
	public void draw(Batch batch, float parentAlpha) {
		validate();

		Color tmp = batch.getColor();
		batch.setColor(Color.WHITE);

		// BACKGROUND
		batch.disableBlending();
		tile.draw(batch, getX(), getY(), getWidth(), getHeight());
		batch.enableBlending();

		if (scn != null && !loading && !loadingError) {
			Vector3 v = new Vector3(getX(), getY(), 0);
			v = v.prj(batch.getTransformMatrix());

			batch.end();

			Gdx.gl.glViewport((int) v.x, (int) v.y, (int) getWidth(), (int) (getHeight()));

			getStage().calculateScissors(bounds, scissors);

			if (ScissorStack.pushScissors(scissors)) {
				// WORLD CAMERA
				sceneBatch.setProjectionMatrix(camera.combined);
				sceneBatch.begin();
				scn.draw(sceneBatch);
				sceneBatch.end();
				ScissorStack.popScissors();
			}

			drawer.drawBGBounds();

			if (showWalkZone && scn.getPolygonalNavGraph() != null) {
				drawer.drawBBoxWalkZone(scn, false);

				drawer.drawPolygonVertices(scn.getPolygonalNavGraph().getWalkZone(), Color.GREEN);
			}

			drawer.drawBBoxActors(scn);

			if (selectedActor != null) {
				drawer.drawSelectedActor(selectedActor);
			}

			getStage().getViewport().apply();

			// SCREEN CAMERA
			batch.begin();

			drawFakeDepthMarkers((SpriteBatch) batch);

			if (!inScene) {
				faRenderer.draw((SpriteBatch) batch);
			}

			// DRAW COORDS
			Vector2 coords = new Vector2(Gdx.input.getX(), Gdx.input.getY());
			screenToWorldCoords(coords);
			String str = MessageFormat.format("({0}, {1})", (int) coords.x, (int) coords.y);

			textLayout.setText(defaultFont, str);

			RectangleRenderer.draw((SpriteBatch) batch, 0f, getY() + getHeight() - textLayout.height - 15,
					textLayout.width + 10, textLayout.height + 10, BLACK_TRANSPARENT);
			defaultFont.draw(batch, textLayout, 5, getHeight() + getY() - 10);

			batch.setColor(tmp);

		} else {
			RectangleRenderer.draw((SpriteBatch) batch, getX(), getY(), getWidth(), getHeight(), Color.BLACK);

			String s;

			if (loading) {
				s = "LOADING...";

				try {
					if (!EngineAssetManager.getInstance().isLoading()) {
						loading = false;

						scn.retrieveAssets();

						drawer.setCamera(camera);

						invalidate();
					}
				} catch (Exception e) {
					Ctx.msg.show(getStage(), "Could not load assets for scene", 4);
					e.printStackTrace();
					loadingError = true;
					loading = false;
				}

			} else if (loadingError) {
				s = "ERROR IN SCENE DATA. CANNOT DISPLAY SCENE";
			} else if (Ctx.project.getProjectDir() == null) {
				s = "CREATE OR LOAD A PROJECT";
			} else {
				s = "THERE ARE NO SCENES IN THIS CHAPTER YET";
			}

			textLayout.setText(bigFont, s);

			bigFont.draw(batch, textLayout, (getWidth() - textLayout.width) / 2,
					getHeight() / 2 + bigFont.getLineHeight() * 3);

		}

	}

	private void drawFakeDepthMarkers(SpriteBatch batch) {
		int margin = 5;

		Vector2 d = scn.getDepthVector();

		if (d == null)
			return;

		tmp2V2.x = 0;
		tmp2V2.y = d.y;
		worldToScreenCoords(tmp2V2);

		String s = "100%";

		textLayout.setText(defaultFont, s);

		float posx = tmp2V2.x - textLayout.width - 20;

		RectangleRenderer.draw((SpriteBatch) batch, posx, tmp2V2.y, textLayout.width + margin * 2,
				textLayout.height + margin * 2, Color.BLACK);
		RectangleRenderer.draw((SpriteBatch) batch, tmp2V2.x - 20, tmp2V2.y, 20, 2, Color.BLACK);

		defaultFont.draw(batch, textLayout, posx + margin, tmp2V2.y + textLayout.height + margin);

		tmp2V2.x = 0;
		tmp2V2.y = d.x;
		worldToScreenCoords(tmp2V2);
		s = "0%";

		textLayout.setText(defaultFont, s);

		posx = tmp2V2.x - textLayout.width - 20;

		RectangleRenderer.draw((SpriteBatch) batch, posx, tmp2V2.y, textLayout.width + margin * 2,
				textLayout.height + margin * 2, Color.BLACK);
		RectangleRenderer.draw((SpriteBatch) batch, tmp2V2.x - 20, tmp2V2.y, 20, 2, Color.BLACK);

		defaultFont.draw(batch, textLayout, posx + margin, tmp2V2.y + textLayout.height + margin);

	}

	public void setInSceneSprites(boolean v) {
		inScene = v;
	}

	public void setAnimation(boolean v) {
		animation = v;
	}

	public void setAnimation(AnimationDesc fa) {
		try {
			faRenderer.setAnimation(fa);
		} catch (Exception e) {
			Ctx.msg.show(getStage(), "Could not retrieve assets for sprite: " + fa.id, 4);
			e.printStackTrace();

			faRenderer.setAnimation(null);
		}
	}

	public void showEditWalkZoneWindow() {
		getParent().addActor(walkZoneWindow);
		showWalkZone = true;
	}

	public void hideEditWalkZoneWindow() {
		getParent().removeActor(walkZoneWindow);
		showWalkZone = false;
	}

	public boolean getShowWalkZone() {
		return showWalkZone;
	}

	@Override
	public void layout() {
		// EditorLogger.debug("LAYOUT SIZE CHANGED - X: " + getX() + " Y: "
		// + getY() + " Width: " + getWidth() + " Height: " + getHeight());
		// EditorLogger.debug("Last Point coords - X: " + (getX() + getWidth())
		// + " Y: " + (getY() + getHeight()));
		localToScreenCoords(tmpV2.set(getX() + getWidth(), getY() + getHeight()));
		// EditorLogger.debug("Screen Last Point coords: " + tmpV2);

		faRenderer.setViewport(getWidth(), getHeight());
		bounds.set(getX(), getY(), getWidth(), getHeight());

		walkZoneWindow.setPosition(getX() + 5, getY() + 5);
		walkZoneWindow.invalidate();

		// SETS WORLD CAMERA
		if (scn != null) {

			float aspect = getWidth() / getHeight();

			float wWidth = Ctx.project.getWorldDocument().getWidth();
			float wHeight = Ctx.project.getWorldDocument().getHeight();
			float aspectWorld = wWidth / wHeight;

			if (aspectWorld > aspect) {
				wHeight = wWidth / aspect;
			} else {
				wWidth = wHeight * aspect;
			}

			zoomLevel = 100;

			camera.setToOrtho(false, wWidth, wHeight);
			camera.zoom = 1f;
			camera.position.set(Ctx.project.getWorldDocument().getWidth() / 2,
					Ctx.project.getWorldDocument().getHeight() / 2, 0);
			camera.update();
			zoom(+1);
		}
	}

	public void zoom(int amount) {
		if (zoomLevel == zoomLevels[0] && amount < 0) {
			zoomLevel = zoomLevels[1];
		} else if (zoomLevel == zoomLevels[zoomLevels.length - 1] && amount > 0) {
			zoomLevel = zoomLevels[zoomLevels.length - 2];
		} else {
			for (int i = 1; i < zoomLevels.length - 1; i++) {
				if (zoomLevels[i] == zoomLevel) {
					zoomLevel = amount > 0 ? zoomLevels[i - 1] : zoomLevels[i + 1];
					break;
				}
			}
		}

		if (scn != null) {
			camera.zoom = 100f / zoomLevel;
			camera.update();
		}
	}

	public void translate(Vector2 delta) {
		// EditorLogger.debug("TRANSLATING - X: " + delta.x + " Y: " + delta.y);
		if (scn != null) {
			camera.translate(-delta.x, -delta.y, 0);
			camera.update();
		}
	}

	public void localToScreenCoords(Vector2 coords) {
		localToStageCoordinates(coords);
		getStage().stageToScreenCoordinates(coords);
	}

	public void localToWorldCoords(Vector2 coords) {
		localToStageCoordinates(coords);
		getStage().stageToScreenCoordinates(coords);

		tmpV3.set(coords.x, coords.y, 0);
		camera.unproject(tmpV3, getX(), getY(), getWidth(), getHeight());
		coords.set(tmpV3.x, tmpV3.y);
	}

	public void screenToWorldCoords(Vector2 coords) {
		tmpV2.set(0, 0);
		localToStageCoordinates(tmpV2);
		// getStage().stageToScreenCoordinates(tmpV2);
		tmpV3.set(coords.x, coords.y, 0);
		camera.unproject(tmpV3, tmpV2.x, tmpV2.y, getWidth(), getHeight());
		coords.set(tmpV3.x, tmpV3.y);
	}

	public void worldToScreenCoords(Vector2 coords) {
		tmpV2.set(getX(), getY());
		localToStageCoordinates(tmpV2);
		tmpV3.set(coords.x, coords.y, 0);
		camera.project(tmpV3, tmpV2.x, tmpV2.y, getWidth(), getHeight());
		coords.set(tmpV3.x, tmpV3.y);
		stageToLocalCoordinates(coords);
	}

	public Scene getScene() {
		return scn;
	}

	public BaseActor getSelectedActor() {
		return selectedActor;
	}

	public void setSelectedScene(Scene s) {
		if (scn != null) {
			scn.dispose();
			faRenderer.dispose();
			scn = null;

			EngineAssetManager.getInstance().clear();
		}

		loadingError = false;

		setSelectedActor(null);

		if (s != null) {
			scn = s;

			scn.loadAssets();
			loading = true;
		}

		walkZoneWindow.setScene(scn);

		// SETS WORLD CAMERA
		if (scn != null) {

			float aspect = getWidth() / getHeight();

			float wWidth = Ctx.project.getWorldDocument().getWidth();
			float wHeight = Ctx.project.getWorldDocument().getHeight();
			float aspectWorld = wWidth / wHeight;

			if (aspectWorld > aspect) {
				wHeight = wWidth / aspect;
			} else {
				wWidth = wHeight * aspect;
			}

			zoomLevel = 100;

			camera.setToOrtho(false, wWidth, wHeight);
			camera.zoom = 1f;
			camera.update();

			// translate(new Vector2((-getWidth() + wWidth ) / 2 *
			// camera.zoom,
			// (-getHeight() + wHeight) / 2 * camera.zoom));

			translate(new Vector2(0, (-getHeight() + wHeight) / 2));
		}
	}

	public void setSelectedActor(BaseActor actor) {
		BaseActor a = null;

		if (scn != null && actor != null) {
			a = actor;
		}

		selectedActor = a;
		faRenderer.setActor(a);
		setAnimation(null);
	}

	public void setSelectedFA(String selFA) {
		if (selectedActor instanceof SpriteActor) {
			ActorRenderer s = ((SpriteActor) selectedActor).getRenderer();

			if (selFA == null || (s.getAnimations().get(selFA) == null
					&& s.getAnimations().get(AnimationDesc.getFlipId(selFA)) == null)) {
				selFA = ((SpriteActor) selectedActor).getRenderer().getInitAnimation();
			}

			if (selFA != null && (s.getAnimations().get(selFA) != null
					|| s.getAnimations().get(AnimationDesc.getFlipId(selFA)) != null)) {

				setAnimation(s.getAnimations().get(selFA));

				if (inScene || s.getCurrentAnimation() == null
						|| ((SpriteActor) selectedActor).getRenderer().getInitAnimation().equals(selFA)) {
					try {

						((SpriteActor) selectedActor).startAnimation(selFA, Tween.Type.REPEAT, Tween.INFINITY, null);
					} catch (Exception e) {
						setAnimation(null);
						((SpriteActor) selectedActor).getRenderer().getAnimations().remove(selFA);
					}
				}
			} else {
				setAnimation(null);
			}
		} else {
			setAnimation(null);
		}
	}

	private void createAndSelectActor(BaseActor actor) {
		// removeActor(Ctx.project.getSelectedChapter(), actor);
		// selectedActor = createActor(actor);
		// setSelectedActor(actor);
	}

	// TODO Mode to the creation method from actorlist
	private BaseActor createActor(BaseActor a) {
		if (a instanceof InteractiveActor) {
			InteractiveActor iActor = (InteractiveActor) a;
			SceneLayer l = scn.getLayer(iActor.getLayer());
			l.orderByZIndex();
		}

		if (a instanceof AssetConsumer) {
			((AssetConsumer) a).loadAssets();
			EngineAssetManager.getInstance().finishLoading();
			((AssetConsumer) a).retrieveAssets();
		}

		return a;
	}

	private void removeActor(ChapterDocument doc, Element e) {
		BaseActor a = scn.getActor(doc.getId(e), false);
		if (a != null) {
			scn.removeActor(a);

			if (a instanceof AssetConsumer)
				((AssetConsumer) a).dispose();

			setSelectedActor(null);
		}
	}

	public void dispose() {
		if (scn != null) {
			scn.dispose();
			scn = null;
		}

		faRenderer.dispose();
	}

}
