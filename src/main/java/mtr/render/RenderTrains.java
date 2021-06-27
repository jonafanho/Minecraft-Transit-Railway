package mtr.render;

import com.mojang.text2speech.Narrator;
import mtr.config.Config;
import mtr.config.CustomResources;
import mtr.data.*;
import mtr.gui.ClientData;
import mtr.gui.IDrawing;
import mtr.item.ItemRailModifier;
import mtr.model.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.MinecartEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RenderTrains implements IGui {

	public static int maxTrainRenderDistance;

	private static final int DETAIL_RADIUS = 32;
	private static final int DETAIL_RADIUS_SQUARED = DETAIL_RADIUS * DETAIL_RADIUS;
	private static final int MAX_RADIUS_REPLAY_MOD = 64 * 16;

	private static final EntityModel<MinecartEntity> MODEL_MINECART = new MinecartEntityModel<>();
	private static final ModelSP1900 MODEL_SP1900 = new ModelSP1900(false);
	private static final ModelSP1900Mini MODEL_SP1900_MINI = new ModelSP1900Mini(false);
	private static final ModelSP1900 MODEL_C1141A = new ModelSP1900(true);
	private static final ModelSP1900Mini MODEL_C1141A_MINI = new ModelSP1900Mini(true);
	private static final ModelMTrain MODEL_M_TRAIN = new ModelMTrain();
	private static final ModelMTrainMini MODEL_M_TRAIN_MINI = new ModelMTrainMini();
	private static final ModelKTrain MODEL_K_TRAIN = new ModelKTrain();
	private static final ModelKTrainMini MODEL_K_TRAIN_MINI = new ModelKTrainMini();
	private static final ModelATrain MODEL_A_TRAIN_TCL = new ModelATrain(false);
	private static final ModelATrainMini MODEL_A_TRAIN_TCL_MINI = new ModelATrainMini(false);
	private static final ModelATrain MODEL_A_TRAIN_AEL = new ModelATrain(true);
	private static final ModelATrainMini MODEL_A_TRAIN_AEL_MINI = new ModelATrainMini(true);
	private static final ModelLightRail1 MODEL_LIGHT_RAIL_1 = new ModelLightRail1();

	public static void render(World world, MatrixStack matrices, VertexConsumerProvider vertexConsumers, Vec3d cameraPos) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}

		if (Config.useDynamicFPS()) {
			final float lastFrameDuration = client.getLastFrameDuration();
			if (lastFrameDuration > 0.8) {
				maxTrainRenderDistance = Math.max(maxTrainRenderDistance - (maxTrainRenderDistance - DETAIL_RADIUS) / 2, DETAIL_RADIUS);
			} else if (lastFrameDuration < 0.5) {
				maxTrainRenderDistance = Math.min(maxTrainRenderDistance + 1, MAX_RADIUS_REPLAY_MOD);
			}
		} else {
			maxTrainRenderDistance = client.options.viewDistance * 8;
		}

		final Vec3d cameraOffset = client.gameRenderer.getCamera().isThirdPerson() ? player.getCameraPosVec(client.getTickDelta()).subtract(cameraPos) : Vec3d.ZERO;

		ClientData.schedulesForPlatform.clear();
		ClientData.sidings.forEach(siding -> siding.simulateTrain(client.player, client.isPaused() ? 0 : client.getLastFrameDuration(), null, (x, y, z, yaw, pitch, customId, trainType, isEnd1Head, isEnd2Head, head1IsFront, doorLeftValue, doorRightValue, opening, lightsOn, offsetRender) -> renderWithLight(world, x, y, z, cameraPos.add(cameraOffset), player, offsetRender, (light, posAverage) -> {
			final ModelTrainBase model = getModel(trainType);

			matrices.push();
			if (offsetRender) {
				matrices.translate(x + cameraOffset.x, y + cameraOffset.y, z + cameraOffset.z);
			} else {
				matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
			}
			matrices.multiply(Vec3f.POSITIVE_Y.getRadialQuaternion((float) Math.PI + yaw));
			matrices.multiply(Vec3f.POSITIVE_X.getRadialQuaternion((float) Math.PI + pitch));

			if (model == null) {
				matrices.translate(0, 0.5, 0);
				matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(90));
				final VertexConsumer vertexConsumer = vertexConsumers.getBuffer(MODEL_MINECART.getLayer(new Identifier("textures/entity/minecart.png")));
				MODEL_MINECART.setAngles(null, 0, 0, -0.1F, 0, 0);
				MODEL_MINECART.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, 1, 1, 1, 1);
			} else {
				model.render(matrices, vertexConsumers, getTrainTexture(customId, trainType.id), light, doorLeftValue, doorRightValue, opening, isEnd1Head, isEnd2Head, head1IsFront, lightsOn, posAverage.getSquaredDistance(player.getBlockPos()) <= DETAIL_RADIUS_SQUARED);
			}

			matrices.pop();
		}), (prevPos1, prevPos2, prevPos3, prevPos4, thisPos1, thisPos2, thisPos3, thisPos4, x, y, z, trainType, lightsOn, offsetRender) -> renderWithLight(world, x, y, z, cameraPos.add(cameraOffset), player, offsetRender, (light, posAverage) -> {
			matrices.push();
			if (offsetRender) {
				matrices.translate(cameraOffset.x, cameraOffset.y, cameraOffset.z);
			} else {
				matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
			}

			final VertexConsumer vertexConsumerExterior = vertexConsumers.getBuffer(MoreRenderLayers.getExterior(new Identifier(getConnectorTextureString(trainType.id, "exterior"))));
			drawTexture(matrices, vertexConsumerExterior, thisPos2, prevPos3, prevPos4, thisPos1, light);
			drawTexture(matrices, vertexConsumerExterior, prevPos2, thisPos3, thisPos4, prevPos1, light);
			drawTexture(matrices, vertexConsumerExterior, prevPos3, thisPos2, thisPos3, prevPos2, light);
			drawTexture(matrices, vertexConsumerExterior, prevPos1, thisPos4, thisPos1, prevPos4, light);

			final int lightOnLevel = lightsOn ? MAX_LIGHT_INTERIOR : light;
			final VertexConsumer vertexConsumerSide = vertexConsumers.getBuffer(MoreRenderLayers.getInterior(new Identifier(getConnectorTextureString(trainType.id, "side"))));
			drawTexture(matrices, vertexConsumerSide, thisPos3, prevPos2, prevPos1, thisPos4, lightOnLevel);
			drawTexture(matrices, vertexConsumerSide, prevPos3, thisPos2, thisPos1, prevPos4, lightOnLevel);
			drawTexture(matrices, vertexConsumers.getBuffer(MoreRenderLayers.getInterior(new Identifier(getConnectorTextureString(trainType.id, "roof")))), prevPos2, thisPos3, thisPos2, prevPos3, lightOnLevel);
			drawTexture(matrices, vertexConsumers.getBuffer(MoreRenderLayers.getInterior(new Identifier(getConnectorTextureString(trainType.id, "floor")))), prevPos4, thisPos1, thisPos4, prevPos1, lightOnLevel);

			matrices.pop();
		}), (speed, stopIndex, routeIds) -> {
			if (!(speed <= 5 && useRoutesAndStationsFromIndex(stopIndex, routeIds, (thisRoute, nextRoute, thisStation, nextStation, lastStation) -> {
				final Text text;
				switch ((int) ((System.currentTimeMillis() / 1000) % 3)) {
					default:
						text = getStationText(thisStation, "this");
						break;
					case 1:
						if (nextStation == null) {
							text = getStationText(thisStation, "this");
						} else {
							text = getStationText(nextStation, "next");
						}
						break;
					case 2:
						text = getStationText(lastStation, "last");
						break;
				}
				player.sendMessage(text, true);
			}))) {
				player.sendMessage(new TranslatableText("gui.mtr.train_speed", Math.round(speed * 10) / 10F, Math.round(speed * 36) / 10F), true);
			}
		}, (stopIndex, routeIds) -> {
			final boolean showAnnouncementMessages = Config.showAnnouncementMessages();
			final boolean useTTSAnnouncements = Config.useTTSAnnouncements();

			if (showAnnouncementMessages || useTTSAnnouncements) {
				useRoutesAndStationsFromIndex(stopIndex, routeIds, (thisRoute, nextRoute, thisStation, nextStation, lastStation) -> {
					final List<String> messages = new ArrayList<>();
					final String fullstopCJK = new TranslatableText("gui.mtr.fullstop_cjk").getString() + " ";
					final String fullstop = new TranslatableText("gui.mtr.fullstop").getString() + " ";

					if (nextStation != null) {
						messages.add(IGui.addToStationName(nextStation.name, new TranslatableText("gui.mtr.next_station_announcement_cjk").getString(), new TranslatableText("gui.mtr.next_station_announcement").getString(), fullstopCJK, fullstop));

						final Map<Integer, ClientData.ColorNamePair> routesInStation = ClientData.routesInStation.get(nextStation.id);
						if (routesInStation != null) {
							final List<String> interchangeRoutes = routesInStation.values().stream().filter(interchangeRoute -> !interchangeRoute.name.split("\\|\\|")[0].equals(thisRoute.name.split("\\|\\|")[0])).map(interchangeRoute -> interchangeRoute.name).collect(Collectors.toList());
							final String mergedStations = IGui.mergeStations(interchangeRoutes).replace(new TranslatableText("gui.mtr.separator_cjk").getString(), ", ").replace(new TranslatableText("gui.mtr.separator").getString(), ", ");
							if (!mergedStations.isEmpty()) {
								messages.add(IGui.addToStationName(mergedStations, new TranslatableText("gui.mtr.interchange_announcement_cjk").getString(), new TranslatableText("gui.mtr.interchange_announcement").getString(), fullstopCJK, fullstop));
							}
						}
					}

					final String message = IGui.formatStationName(IGui.mergeStations(messages).replace(new TranslatableText("gui.mtr.separator_cjk").getString(), "").replace(new TranslatableText("gui.mtr.separator").getString(), "")).replace("  ", " ");
					if (useTTSAnnouncements) {
						Narrator.getNarrator().say(message, false);
					}
					if (showAnnouncementMessages) {
						player.sendMessage(Text.of(message), false);
					}
				});
			}
		}, (platformId, arrivalMillis, departureMillis, trainType, stopIndex, routeIds) -> useRoutesAndStationsFromIndex(stopIndex, routeIds, (thisRoute, nextRoute, thisStation, nextStation, lastStation) -> {
			if (lastStation != null) {
				if (!ClientData.schedulesForPlatform.containsKey(platformId)) {
					ClientData.schedulesForPlatform.put(platformId, new HashSet<>());
				}
				ClientData.schedulesForPlatform.get(platformId).add(new Route.ScheduleEntry(arrivalMillis, departureMillis, trainType, platformId, lastStation.name, nextStation == null));
			}
		}), ClientData::updateSidings));

		final VertexConsumer vertexConsumer = vertexConsumers.getBuffer(MoreRenderLayers.getExterior(new Identifier("textures/block/rail.png")));
		matrices.translate(-cameraPos.x, 0.0625 + SMALL_OFFSET - cameraPos.y, -cameraPos.z);
		final boolean renderColors = player.isHolding(item -> item instanceof ItemRailModifier);
		final int maxRailDistance = client.options.viewDistance * 16;
		ClientData.rails.forEach((startPos, railMap) -> railMap.forEach((endPos, rail) -> {
			if (!RailwayData.isBetween(player.getX(), startPos.getX(), endPos.getX(), maxRailDistance) || !RailwayData.isBetween(player.getZ(), startPos.getZ(), endPos.getZ(), maxRailDistance)) {
				return;
			}

			rail.render((x1, z1, x2, z2, x3, z3, x4, z4, y1, y2) -> {
				if (shouldNotRender(player, new BlockPos(x1, y1, z1), maxRailDistance)) {
					return;
				}

				final float textureOffset = (((int) (x1 + z1)) % 4) * 0.25F;
				final BlockPos pos2 = new BlockPos(x1, y1, z1);
				final int light2 = LightmapTextureManager.pack(world.getLightLevel(LightType.BLOCK, pos2), world.getLightLevel(LightType.SKY, pos2));
				final int color = renderColors || rail.railType.hasSavedRail ? rail.railType.color : -1;

				IDrawing.drawTexture(matrices, vertexConsumer, x1, y1, z1, x2, y1 + SMALL_OFFSET, z2, x3, y2, z3, x4, y2 + SMALL_OFFSET, z4, 0, 0.1875F + textureOffset, 1, 0.3125F + textureOffset, Direction.UP, color, light2);
				IDrawing.drawTexture(matrices, vertexConsumer, x4, y2 + SMALL_OFFSET, z4, x3, y2, z3, x2, y1 + SMALL_OFFSET, z2, x1, y1, z1, 0, 0.1875F + textureOffset, 1, 0.3125F + textureOffset, Direction.UP, color, light2);
				
				IDrawing.drawTexture(matrices, vertexConsumer, x1, y1, z1, x2, y1 + SMALL_OFFSET/2, z2, x3, y2, z3, x4, y2 + SMALL_OFFSET/2, z4, 0, 0, 1, 1, Direction.UP, color, light2);
				IDrawing.drawTexture(matrices, vertexConsumer, x4, y2 + SMALL_OFFSET/2, z4, x3, y2, z3, x2, y1 + SMALL_OFFSET/2, z2, x1, y1, z1, 0, 0, 1, 1, Direction.UP, color, light2);
			});
		}));
	}

	public static boolean shouldNotRender(PlayerEntity player, BlockPos pos, int maxDistance) {
		return player == null || player.getBlockPos().getManhattanDistance(pos) > (isReplayMod(player) ? MAX_RADIUS_REPLAY_MOD : maxDistance);
	}

	public static boolean shouldNotRender(BlockPos pos, int maxDistance) {
		final PlayerEntity player = MinecraftClient.getInstance().player;
		return shouldNotRender(player, pos, maxDistance);
	}

	public static boolean useRoutesAndStationsFromIndex(int stopIndex, List<Long> routeIds, RouteAndStationsCallback routeAndStationsCallback) {
		if (stopIndex < 0) {
			return false;
		}

		int sum = 0;
		for (int i = 0; i < routeIds.size(); i++) {
			final Route thisRoute = ClientData.routeIdMap.get(routeIds.get(i));
			final Route nextRoute = i < routeIds.size() - 1 ? ClientData.routeIdMap.get(routeIds.get(i + 1)) : null;
			if (thisRoute != null) {
				final int difference = stopIndex - sum;
				sum += thisRoute.platformIds.size();
				if (!thisRoute.platformIds.isEmpty() && nextRoute != null && !nextRoute.platformIds.isEmpty() && thisRoute.platformIds.get(thisRoute.platformIds.size() - 1).equals(nextRoute.platformIds.get(0))) {
					sum--;
				}
				if (stopIndex < sum) {
					final Station thisStation = ClientData.platformIdToStation.get(thisRoute.platformIds.get(difference));
					final Station nextStation = difference < thisRoute.platformIds.size() - 1 ? ClientData.platformIdToStation.get(thisRoute.platformIds.get(difference + 1)) : null;
					final Station lastStation = thisRoute.platformIds.isEmpty() ? null : ClientData.platformIdToStation.get(thisRoute.platformIds.get(thisRoute.platformIds.size() - 1));
					routeAndStationsCallback.routeAndStationsCallback(thisRoute, nextRoute, thisStation, nextStation, lastStation);
					return true;
				}
			}
		}
		return false;
	}

	private static void renderWithLight(World world, float x, float y, float z, Vec3d cameraPos, PlayerEntity player, boolean offsetRender, RenderCallback renderCallback) {
		final BlockPos posAverage = offsetRender ? new BlockPos(cameraPos).add(x, y, z) : new BlockPos(x, y, z);
		if (!shouldNotRender(player, posAverage, maxTrainRenderDistance)) {
			renderCallback.renderCallback(LightmapTextureManager.pack(world.getLightLevel(LightType.BLOCK, posAverage), world.getLightLevel(LightType.SKY, posAverage)), posAverage);
		}
	}

	private static Text getStationText(Station station, String textKey) {
		if (station != null) {
			return Text.of(IGui.formatStationName(IGui.addToStationName(IGui.textOrUntitled(station.name), new TranslatableText("gui.mtr." + textKey + "_station_cjk").getString(), new TranslatableText("gui.mtr." + textKey + "_station").getString(), "", "")));
		} else {
			return new LiteralText("");
		}
	}

	private static void drawTexture(MatrixStack matrices, VertexConsumer vertexConsumer, Pos3f pos1, Pos3f pos2, Pos3f pos3, Pos3f pos4, int light) {
		IDrawing.drawTexture(matrices, vertexConsumer, pos1.x, pos1.y, pos1.z, pos2.x, pos2.y, pos2.z, pos3.x, pos3.y, pos3.z, pos4.x, pos4.y, pos4.z, 0, 0, 1, 1, Direction.UP, -1, light);
	}

	private static boolean isReplayMod(PlayerEntity playerEntity) {
		return playerEntity.getClass().toGenericString().toLowerCase().contains("replaymod");
	}

	private static Identifier getTrainTexture(String customId, String trainId) {
		if (customId.isEmpty() || !CustomResources.customTrains.containsKey(customId)) {
			return new Identifier("mtr:textures/entity/" + trainId + ".png");
		} else {
			return CustomResources.customTrains.get(customId).textureId;
		}
	}

	private static String getConnectorTextureString(String trainId, String connectorPart) {
		return "mtr:textures/entity/" + trainId + "_connector_" + connectorPart + ".png";
	}

	private static ModelTrainBase getModel(TrainType trainType) {
		switch (trainType) {
			case SP1900:
				return MODEL_SP1900;
			case C1141A:
				return MODEL_C1141A;
			case SP1900_MINI:
				return MODEL_SP1900_MINI;
			case C1141A_MINI:
				return MODEL_C1141A_MINI;
			case M_TRAIN:
				return MODEL_M_TRAIN;
			case M_TRAIN_MINI:
				return MODEL_M_TRAIN_MINI;
			case K_TRAIN:
				return MODEL_K_TRAIN;
			case K_TRAIN_MINI:
				return MODEL_K_TRAIN_MINI;
			case A_TRAIN_TCL:
				return MODEL_A_TRAIN_TCL;
			case A_TRAIN_TCL_MINI:
				return MODEL_A_TRAIN_TCL_MINI;
			case A_TRAIN_AEL:
				return MODEL_A_TRAIN_AEL;
			case A_TRAIN_AEL_MINI:
				return MODEL_A_TRAIN_AEL_MINI;
			case LIGHT_RAIL_1:
				return MODEL_LIGHT_RAIL_1;
			default:
				return null;
		}
	}

	@FunctionalInterface
	public interface RouteAndStationsCallback {
		void routeAndStationsCallback(Route thisRoute, Route nextRoute, Station thisStation, Station nextStation, Station lastStation);
	}

	@FunctionalInterface
	private interface RenderCallback {
		void renderCallback(int light, BlockPos posAverage);
	}
}
