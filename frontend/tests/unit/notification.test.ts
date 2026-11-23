import { expect, it } from "vitest";
import { api } from "../../src/api";
import { http } from "../../src/api/client";
import { time } from "../../src/lib/presentation";

it("消息中心使用本人通知列表和详情路径", async () => {
  const seen: string[] = [];
  http.defaults.adapter = async (config) => {
    seen.push(`${config.method}:${config.url}`);
    const data =
      config.url === "/notifications"
        ? []
        : {
            id: "9",
            type: "CHECKED_IN",
            title: "签到成功",
            patientName: "就诊人甲",
            occurredAt: "2026-11-13T07:35:00",
            detail: "就诊人甲已完成到院签到。",
            appointmentId: "31",
          };
    return {
      config,
      data: { code: 0, message: "success", data },
      status: 200,
      statusText: "OK",
      headers: {},
    };
  };

  await expect(api.notifications()).resolves.toEqual([]);
  const detail = await api.notification("9");

  expect(detail.appointmentId).toBe("31");
  expect(detail.type).toBe("CHECKED_IN");
  expect(seen).toEqual(["get:/notifications", "get:/notifications/9"]);
});

it("通知发生时间按业务时间展示", () => {
  expect(time("2026-11-13T07:35:00")).toBe("2026-11-13 07:35:00");
});
