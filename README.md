# Minecraft 百科查询

可以戳一戳机器人获取帮助

## 指令

```shell
# 修改搜索标签的命令
/mcmod setQueryCommand <type> <command>
/mcmod 查询命令 <type> <command> 
# 是否启用戳一戳回复功能 true:启用 false:禁用
/mcmod setNudgeEnabled <enabled>
```

type有如下类型
 - [ALL] 全部
 - [MODULE] 模组
 - [INTEGRATION_PACKAGE] 整合包
 - [DATA] 资料
 - [COURSE_OF_STUDY] 教程
 - [AUTHOR] 作者
 - [USER] 用户
 - [COMMUNITY] 社群
 - [SERVER] 服务器

现只支持 `MODULE` `DATA` `COURSE_OF_STUDY` `INTEGRATION_PACKAGE` `SERVER`

默认命令：

MODULE = 百科模组

DATA = 百科资料

COURSE_OF_STUDY = 百科教程

INTEGRATION_PACKAGE = 百科整合包

SERVER = 百科服务器

