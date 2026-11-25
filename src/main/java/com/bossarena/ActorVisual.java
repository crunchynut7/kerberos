package com.bossarena;

import com.bossarena.engine.Entity;
import com.bossarena.engine.Tile;
import java.awt.Shape;
import java.util.List;
import lombok.Getter;
import net.runelite.api.Animation;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/**
 * Draws one engine entity in the scene as a client-side object, and keeps its position, facing and
 * animation in step with the engine.
 */
class ActorVisual
{
	private static final int FULL_TURN = 2048;
	/** How far a character may turn per client tick. */
	private static final int TURN_SPEED = 96;

	private final Client client;
	@Getter
	private final Entity entity;
	private final ArenaObject object;
	private final int idleAnimation;
	private final int walkAnimation;
	private final int runAnimation;
	@Getter
	private final int height;

	private int orientation;
	private int poseAnimation = Integer.MIN_VALUE;
	private int animationSequence;

	/**
	 * @param model the model to draw, or null to draw nothing (the overlay still marks the tile)
	 */
	ActorVisual(Client client, Entity entity, Model model, int idleAnimation, int walkAnimation, int runAnimation)
	{
		this.client = client;
		this.entity = entity;
		this.idleAnimation = idleAnimation;
		this.walkAnimation = walkAnimation != -1 ? walkAnimation : idleAnimation;
		this.runAnimation = runAnimation != -1 ? runAnimation : this.walkAnimation;
		this.animationSequence = entity.getAnimationSequence();
		this.orientation = 0;
		if (model != null)
		{
			object = new ArenaObject(client);
			object.setModel(model);
			height = model.getModelHeight();
		}
		else
		{
			object = null;
			height = 180 * entity.getSize();
		}
	}

	/** For an entity the overlay draws by hand: there is no model, only a height to hang the health bar from. */
	ActorVisual(Client client, Entity entity, int height)
	{
		this.client = client;
		this.entity = entity;
		this.object = null;
		this.idleAnimation = -1;
		this.walkAnimation = -1;
		this.runAnimation = -1;
		this.height = height;
		this.animationSequence = entity.getAnimationSequence();
	}

	/**
	 * @param progress how far through the current engine tick we are, from 0 to 1
	 */
	void update(ArenaView view, double progress)
	{
		if (object == null)
		{
			return;
		}
		double[] at = ArenaView.interpolate(entity, progress);
		LocalPoint location = view.toLocal(client, at[0], at[1], entity.getSize());
		if (location == null || entity.isDead())
		{
			object.setActive(false);
			return;
		}

		object.setLocation(location, view.plane());
		turnTowards(targetOrientation(progress));
		object.setOrientation(orientation);
		updatePose();
		updateAnimation();
		object.setActive(true);
	}

	/**
	 * The outline of the drawn model on screen, so floor art can be kept from painting over it.
	 *
	 * @return null if nothing is drawn
	 */
	Shape hull()
	{
		if (object == null || !object.isActive())
		{
			return null;
		}
		Model model = object.getModel();
		WorldView worldView = client.getTopLevelWorldView();
		if (model == null || worldView == null)
		{
			return null;
		}
		return Perspective.getClickbox(client, worldView, model, object.getOrientation(),
			object.getX(), object.getY(), object.getZ());
	}

	void remove()
	{
		if (object != null)
		{
			object.setActive(false);
		}
	}

	private int targetOrientation(double progress)
	{
		List<Tile> steps = entity.getSteps();
		if (!steps.isEmpty())
		{
			int index = Math.min((int) (Math.max(0, Math.min(1, progress)) * steps.size()), steps.size() - 1);
			Tile from = index == 0 ? entity.getPreviousTile() : steps.get(index - 1);
			Tile to = steps.get(index);
			return angle(to.getX() - from.getX(), to.getY() - from.getY());
		}
		Entity facing = entity.getFacing();
		if (facing != null)
		{
			double[] self = ArenaView.centre(entity, progress);
			double[] other = ArenaView.centre(facing, progress);
			double dx = other[0] - self[0];
			double dy = other[1] - self[1];
			if (Math.abs(dx) > 0.01 || Math.abs(dy) > 0.01)
			{
				return angle(dx, dy);
			}
		}
		return orientation;
	}

	/** Game angle for a direction: 0 is south, 512 west, 1024 north, 1536 east. */
	private static int angle(double dx, double dy)
	{
		return (int) Math.round(Math.atan2(-dx, -dy) * (FULL_TURN / 2) / Math.PI) & (FULL_TURN - 1);
	}

	private void turnTowards(int target)
	{
		int difference = ((target - orientation + FULL_TURN / 2) & (FULL_TURN - 1)) - FULL_TURN / 2;
		int turn = Math.max(-TURN_SPEED, Math.min(TURN_SPEED, difference));
		orientation = (orientation + turn) & (FULL_TURN - 1);
	}

	private void updatePose()
	{
		int steps = entity.getSteps().size();
		int wanted = steps == 0 ? idleAnimation : steps == 1 ? walkAnimation : runAnimation;
		if (wanted == poseAnimation)
		{
			return;
		}
		poseAnimation = wanted;
		Animation animation = wanted == -1 ? null : client.loadAnimation(wanted);
		if (animation == null)
		{
			object.setPoseAnimationController(null);
			return;
		}
		AnimationController controller = new AnimationController(client, animation);
		controller.setOnFinished(AnimationController::loop);
		object.setPoseAnimationController(controller);
	}

	private void updateAnimation()
	{
		if (entity.getAnimationSequence() == animationSequence)
		{
			return;
		}
		animationSequence = entity.getAnimationSequence();
		Animation animation = entity.getAnimation() == -1 ? null : client.loadAnimation(entity.getAnimation());
		if (animation == null)
		{
			object.setAnimationController(null);
			return;
		}
		AnimationController controller = new AnimationController(client, animation);
		controller.setOnFinished(finished -> object.setAnimationController(null));
		object.setAnimationController(controller);
	}

	/** A RuneLiteObject that also advances its pose animation, which the base class leaves to the caller. */
	private static final class ArenaObject extends RuneLiteObject
	{
		private ArenaObject(Client client)
		{
			super(client);
		}

		@Override
		public void tick(int ticksSinceLastFrame)
		{
			super.tick(ticksSinceLastFrame);
			AnimationController pose = getPoseAnimationController();
			if (pose != null)
			{
				pose.tick(ticksSinceLastFrame);
			}
		}
	}
}
